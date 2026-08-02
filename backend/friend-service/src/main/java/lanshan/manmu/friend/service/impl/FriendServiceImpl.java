package lanshan.manmu.friend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.common.rpc.dto.friend.*;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoReq;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoResp;
import lanshan.manmu.common.rpc.dto.user.UserInfo;
import lanshan.manmu.common.util.SnowflakeIdWorker;
import lanshan.manmu.friend.mapper.FriendGroupMapper;
import lanshan.manmu.friend.mapper.FriendMapper;
import lanshan.manmu.friend.mapper.FriendRequestMapper;
import lanshan.manmu.friend.mapper.UserBlockMapper;
import lanshan.manmu.friend.model.entity.Friend;
import lanshan.manmu.friend.model.entity.FriendGroup;
import lanshan.manmu.friend.model.entity.FriendRequest;
import lanshan.manmu.friend.model.entity.UserBlock;
import lanshan.manmu.friend.service.FriendService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 好友服务实现，行为对齐契约 §4（前端 mock {@code mocks/db/social.ts} 为事实来源）。
 * <ul>
 *   <li>好友关系双向存储（friends 表 user_id→friend_id 各一条）</li>
 *   <li>申请状态 1=待处理 2=已接受 3=已拒绝 4=已取消</li>
 *   <li>pending 幂等：部分唯一索引 (from_user_id, to_user_id) WHERE status=1 防并发双申请，
 *       撞唯一键时捕获 {@link DuplicateKeyException} 重查既有申请返回</li>
 *   <li>接受/拒绝/取消用条件 UPDATE（status=1），并发下自然得到 20002</li>
 * </ul>
 */
@Service
@Slf4j
public class FriendServiceImpl implements FriendService {

    /** 内置默认分组（契约 §4：groupId='0'） */
    private static final long DEFAULT_GROUP_ID = 0L;
    private static final String DEFAULT_GROUP_NAME = "默认分组";
    /** 申请状态 */
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_ACCEPTED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_CANCELED = 4;
    /** 列表方向（契约 §4） */
    private static final String DIRECTION_INCOMING = "incoming";
    /** Phase 1 在线状态固定值：signaling-service 尚未实现在线状态，Phase B 接入 presence */
    private static final String STATUS_OFFLINE = "offline";
    /** 分页默认值与钳制（契约 §1.4） */
    private static final int DEFAULT_PAGE_SIZE_REQUESTS = 50;
    private static final int DEFAULT_PAGE_SIZE_FRIENDS = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final FriendMapper friendMapper;
    private final FriendGroupMapper groupMapper;
    private final FriendRequestMapper requestMapper;
    private final UserBlockMapper blockMapper;
    private final SnowflakeIdWorker snowflake;
    private final UserRpcService userRpcService;

    public FriendServiceImpl(FriendMapper friendMapper,
                             FriendGroupMapper groupMapper,
                             FriendRequestMapper requestMapper,
                             UserBlockMapper blockMapper,
                             SnowflakeIdWorker snowflake,
                             UserRpcService userRpcService) {
        this.friendMapper = friendMapper;
        this.groupMapper = groupMapper;
        this.requestMapper = requestMapper;
        this.blockMapper = blockMapper;
        this.snowflake = snowflake;
        this.userRpcService = userRpcService;
    }

    /**
     * Dubbo 引用声明（构造器注入适配），与 conv-service 同款 Java-config 模式：
     * {@code @DubboReference} 标注在 {@code @Bean} 方法上返回 {@code ReferenceBean}，
     * 由 Dubbo 注册为可按类型注入的 bean，消除字段注入（AGENTS.md 规范）。
     */
    @Configuration
    static class DubboReferenceConfig {
        @Bean
        @DubboReference
        public ReferenceBean<UserRpcService> userRpcReference() {
            return new ReferenceBean<>();
        }
    }

    // ==================== 好友申请 ====================

    @Override
    @Transactional
    public SendFriendRequestResp sendFriendRequest(SendFriendRequestReq req) {
        long fromUserId = req.getFromUserId();
        long toUserId = req.getToUserId();
        requireUserExists(toUserId);
        if (fromUserId == toUserId) {
            throw new BizException(ErrorCode.FRIEND_SELF);
        }
        if (isFriend(fromUserId, toUserId)) {
            throw new BizException(ErrorCode.FRIEND_ALREADY_EXISTS);
        }
        if (isBlocked(fromUserId, toUserId)) {
            throw new BizException(ErrorCode.BLOCKED_BY_YOU);
        }
        if (isBlocked(toUserId, fromUserId)) {
            throw new BizException(ErrorCode.BLOCKED_BY_THEM);
        }
        // 幂等：存在 pending 时刷新 message/updatedAt，返回原申请
        FriendRequest pending = findPending(fromUserId, toUserId);
        if (pending != null) {
            pending.setMessage(req.getMessage());
            pending.setUpdatedAt(OffsetDateTime.now());
            requestMapper.updateById(pending);
            return new SendFriendRequestResp(pending.getId());
        }
        FriendRequest row = new FriendRequest(snowflake.nextId(), fromUserId, toUserId,
                req.getMessage(), STATUS_PENDING, OffsetDateTime.now(), OffsetDateTime.now());
        try {
            requestMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发双申请：部分唯一索引 (from,to) WHERE status=1 兜底，重查既有 pending 幂等返回
            log.info("duplicate friend request from={} to={}, return existing", fromUserId, toUserId);
            FriendRequest existing = findPending(fromUserId, toUserId);
            if (existing != null) {
                return new SendFriendRequestResp(existing.getId());
            }
            throw e;
        }
        return new SendFriendRequestResp(row.getId());
    }

    @Override
    public ListFriendRequestsResp listFriendRequests(ListFriendRequestsReq req) {
        boolean incoming = DIRECTION_INCOMING.equals(req.getDirection());
        int pageNum = clampPageNum(req.getPageNum());
        int pageSize = clampPageSize(req.getPageSize(), DEFAULT_PAGE_SIZE_REQUESTS);
        Page<FriendRequest> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FriendRequest> qw = new LambdaQueryWrapper<>();
        if (incoming) {
            // 待处理：仅 status=1 的 incoming（契约 §4）
            qw.eq(FriendRequest::getToUserId, req.getUserId()).eq(FriendRequest::getStatus, STATUS_PENDING);
        } else {
            // 已发送：outgoing 全状态
            qw.eq(FriendRequest::getFromUserId, req.getUserId());
        }
        qw.orderByDesc(FriendRequest::getCreatedAt);
        requestMapper.selectPage(page, qw);
        List<FriendRequestDTO> list = page.getRecords().stream().map(this::toRequestDto).toList();
        return new ListFriendRequestsResp(list, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public FriendRequestDTO acceptFriendRequest(AcceptFriendRequestReq req) {
        // 条件 UPDATE status=1 → 2（并发下已处理/重复接受自然 20002）
        FriendRequest row = transition(req.getRequestId(), req.getUserId(), false, STATUS_ACCEPTED);
        addFriendPair(row.getFromUserId(), row.getToUserId());
        return toRequestDto(row);
    }

    @Override
    @Transactional
    public FriendRequestDTO rejectFriendRequest(RejectFriendRequestReq req) {
        FriendRequest row = transition(req.getRequestId(), req.getUserId(), false, STATUS_REJECTED);
        return toRequestDto(row);
    }

    @Override
    @Transactional
    public void cancelFriendRequest(CancelFriendRequestReq req) {
        transition(req.getRequestId(), req.getUserId(), true, STATUS_CANCELED);
    }

    // ==================== 好友管理 ====================

    @Override
    public ListFriendsResp listFriends(ListFriendsReq req) {
        int pageNum = clampPageNum(req.getPageNum());
        int pageSize = clampPageSize(req.getPageSize(), DEFAULT_PAGE_SIZE_FRIENDS);
        Page<Friend> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Friend> qw = new LambdaQueryWrapper<Friend>().eq(Friend::getUserId, req.getUserId());
        // groupId 缺省或 0：返回全部好友（mock 行为）；指定分组才过滤
        Long groupId = req.getGroupId();
        if (groupId != null && groupId != DEFAULT_GROUP_ID) {
            qw.eq(Friend::getGroupId, groupId);
        }
        qw.orderByAsc(Friend::getCreatedAt);
        friendMapper.selectPage(page, qw);

        Map<Long, String> groupNames = groupNameMap(req.getUserId());
        Map<Long, UserInfo> users = fetchUsers(page.getRecords().stream().map(Friend::getFriendId).toList());
        List<FriendInfoDTO> list = page.getRecords().stream()
                .map(f -> toFriendDto(f, users, groupNames)).toList();
        return new ListFriendsResp(list, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public void deleteFriend(DeleteFriendReq req) {
        long a = req.getUserId();
        long b = req.getFriendUserId();
        if (friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, a).eq(Friend::getFriendId, b)) == 0) {
            throw new BizException(ErrorCode.NOT_FRIEND);
        }
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, b).eq(Friend::getFriendId, a));
    }

    @Override
    public void setRemark(SetRemarkReq req) {
        Friend friend = requireFriend(req.getUserId(), req.getFriendUserId());
        friend.setRemark(req.getRemark());
        friendMapper.updateById(friend);
    }

    @Override
    public void moveGroup(MoveGroupReq req) {
        Friend friend = requireFriend(req.getUserId(), req.getFriendUserId());
        if (req.getGroupId() != DEFAULT_GROUP_ID) {
            requireOwnGroup(req.getUserId(), req.getGroupId());
        }
        friend.setGroupId(req.getGroupId());
        friendMapper.updateById(friend);
    }

    // ==================== 好友分组 ====================

    @Override
    public ListGroupsResp listGroups(ListGroupsReq req) {
        List<FriendGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<FriendGroup>()
                .eq(FriendGroup::getUserId, req.getUserId())
                .orderByAsc(FriendGroup::getCreatedAt));
        Map<Long, Integer> countBy = friendCountByGroup(req.getUserId());
        List<FriendGroupDTO> list = new ArrayList<>();
        // 内置默认分组（契约 §4：groupId=0，createdAt=0）
        list.add(new FriendGroupDTO(DEFAULT_GROUP_ID, DEFAULT_GROUP_NAME,
                countBy.getOrDefault(DEFAULT_GROUP_ID, 0), 0L));
        for (FriendGroup g : groups) {
            list.add(new FriendGroupDTO(g.getId(), g.getName(),
                    countBy.getOrDefault(g.getId(), 0), millis(g.getCreatedAt())));
        }
        return new ListGroupsResp(list, list.size());
    }

    @Override
    public CreateGroupResp createGroup(CreateGroupReq req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            // 契约 §4：name 空则落"新建分组"
            name = "新建分组";
        }
        FriendGroup group = new FriendGroup(snowflake.nextId(), req.getUserId(), name,
                req.getSortOrder(), OffsetDateTime.now());
        groupMapper.insert(group);
        return new CreateGroupResp(group.getId(), group.getName());
    }

    @Override
    public RenameGroupResp renameGroup(RenameGroupReq req) {
        FriendGroup group = requireOwnGroup(req.getUserId(), req.getGroupId());
        group.setName(req.getName());
        groupMapper.updateById(group);
        return new RenameGroupResp(group.getId(), group.getName());
    }

    @Override
    @Transactional
    public void deleteGroup(DeleteGroupReq req) {
        FriendGroup group = requireOwnGroup(req.getUserId(), req.getGroupId());
        groupMapper.deleteById(group.getId());
        // 组内好友回落默认分组（契约 §4）
        friendMapper.update(null, new LambdaUpdateWrapper<Friend>()
                .eq(Friend::getUserId, req.getUserId())
                .eq(Friend::getGroupId, req.getGroupId())
                .set(Friend::getGroupId, DEFAULT_GROUP_ID));
    }

    // ==================== 黑名单 ====================

    @Override
    @Transactional
    public void blockUser(BlockUserReq req) {
        long me = req.getUserId();
        long target = req.getTargetUserId();
        requireUserExists(target);
        if (me == target) {
            throw new BizException(ErrorCode.FRIEND_SELF);
        }
        insertBlockIfAbsent(me, target);
        // 拉黑即解除好友关系，并取消双方待处理申请（契约 §4 / mock social.ts）
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, me).eq(Friend::getFriendId, target));
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, target).eq(Friend::getFriendId, me));
        cancelPendingBetween(me, target);
    }

    @Override
    public void unblockUser(UnblockUserReq req) {
        if (blockMapper.delete(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, req.getUserId())
                .eq(UserBlock::getBlockedUserId, req.getTargetUserId())) == 0) {
            throw new BizException(ErrorCode.NOT_BLOCKED);
        }
    }

    @Override
    public ListBlacklistResp listBlacklist(ListBlacklistReq req) {
        int pageNum = clampPageNum(req.getPageNum());
        int pageSize = clampPageSize(req.getPageSize(), DEFAULT_PAGE_SIZE_FRIENDS);
        Page<UserBlock> page = new Page<>(pageNum, pageSize);
        blockMapper.selectPage(page, new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, req.getUserId())
                .orderByDesc(UserBlock::getCreatedAt));
        Map<Long, UserInfo> users = fetchUsers(page.getRecords().stream()
                .map(UserBlock::getBlockedUserId).toList());
        List<BlacklistEntryDTO> list = page.getRecords().stream().map(b -> {
            UserInfo u = users.get(b.getBlockedUserId());
            return new BlacklistEntryDTO(b.getBlockedUserId(),
                    u != null ? u.getUsername() : "未知用户",
                    u != null ? u.getAvatar() : "",
                    millis(b.getCreatedAt()));
        }).toList();
        return new ListBlacklistResp(list, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public boolean isBlocked(IsBlockedReq req) {
        return isBlocked(req.getUserId(), req.getTargetUserId());
    }

    // ==================== 私有辅助 ====================

    /** 申请状态流转：条件 UPDATE（status=1），影响 0 行 → 20002；返回流转后的完整记录 */
    private FriendRequest transition(long requestId, long operatorId, boolean operatorIsSender, int newStatus) {
        LambdaUpdateWrapper<FriendRequest> uw = new LambdaUpdateWrapper<>();
        uw.eq(FriendRequest::getId, requestId)
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .set(FriendRequest::getStatus, newStatus)
                .set(FriendRequest::getUpdatedAt, OffsetDateTime.now());
        // 权限分支：接受/拒绝仅收件人，取消仅发起人（不用三元方法引用，避免 lambda 序列化歧义）
        if (operatorIsSender) {
            uw.eq(FriendRequest::getFromUserId, operatorId);
        } else {
            uw.eq(FriendRequest::getToUserId, operatorId);
        }
        if (requestMapper.update(null, uw) == 0) {
            throw new BizException(ErrorCode.FRIEND_REQUEST_HANDLED);
        }
        return requestMapper.selectById(requestId);
    }

    private FriendRequest findPending(long fromUserId, long toUserId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUserId, fromUserId)
                .eq(FriendRequest::getToUserId, toUserId)
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .last("LIMIT 1"));
    }

    private void addFriendPair(long a, long b) {
        insertFriendIfAbsent(a, b);
        insertFriendIfAbsent(b, a);
    }

    private void insertFriendIfAbsent(long ownerId, long friendId) {
        if (isFriend(ownerId, friendId)) {
            return;
        }
        try {
            friendMapper.insert(new Friend(snowflake.nextId(), ownerId, friendId,
                    DEFAULT_GROUP_ID, "", OffsetDateTime.now()));
        } catch (DuplicateKeyException e) {
            // 并发已建好友，忽略
            log.info("friend pair already exists owner={} friend={}", ownerId, friendId);
        }
    }

    private boolean isFriend(long userId, long friendId) {
        return friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId)) > 0;
    }

    private Friend requireFriend(long userId, long friendId) {
        Friend friend = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId));
        if (friend == null) {
            throw new BizException(ErrorCode.NOT_FRIEND);
        }
        return friend;
    }

    private FriendGroup requireOwnGroup(long userId, long groupId) {
        FriendGroup group = groupMapper.selectOne(new LambdaQueryWrapper<FriendGroup>()
                .eq(FriendGroup::getId, groupId).eq(FriendGroup::getUserId, userId));
        if (group == null) {
            throw new BizException(ErrorCode.FRIEND_GROUP_NOT_FOUND);
        }
        return group;
    }

    private boolean isBlocked(long userId, long targetUserId) {
        return blockMapper.selectCount(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, userId)
                .eq(UserBlock::getBlockedUserId, targetUserId)) > 0;
    }

    private void insertBlockIfAbsent(long userId, long targetUserId) {
        if (isBlocked(userId, targetUserId)) {
            return;
        }
        try {
            blockMapper.insert(new UserBlock(snowflake.nextId(), userId, targetUserId, OffsetDateTime.now()));
        } catch (DuplicateKeyException e) {
            // 重复拉黑幂等（并发兜底）
            log.info("block already exists user={} target={}", userId, targetUserId);
        }
    }

    /** 取消双方待处理申请（拉黑副作用） */
    private void cancelPendingBetween(long a, long b) {
        requestMapper.update(null, new LambdaUpdateWrapper<FriendRequest>()
                .eq(FriendRequest::getStatus, STATUS_PENDING)
                .and(w -> w.eq(FriendRequest::getFromUserId, a).eq(FriendRequest::getToUserId, b)
                        .or().eq(FriendRequest::getFromUserId, b).eq(FriendRequest::getToUserId, a))
                .set(FriendRequest::getStatus, STATUS_CANCELED)
                .set(FriendRequest::getUpdatedAt, OffsetDateTime.now()));
    }

    /** 目标用户必须存在（mock requireUser → 10001；batch 语义为不存在则缺失） */
    private void requireUserExists(long userId) {
        try {
            BatchGetUserInfoResp resp = userRpcService.batchGetUserInfo(new BatchGetUserInfoReq(List.of(userId)));
            if (resp == null || resp.getUsers() == null || resp.getUsers().isEmpty()) {
                throw new BizException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("check user exists failed userId={}", userId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "user service unavailable");
        }
    }

    /** 批量补全用户信息；RPC 失败降级为空 Map（补全失败不阻断主流程，与 conv resolvePeerUsername 一致） */
    private Map<Long, UserInfo> fetchUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            BatchGetUserInfoResp resp = userRpcService.batchGetUserInfo(new BatchGetUserInfoReq(userIds));
            Map<Long, UserInfo> map = new HashMap<>();
            if (resp != null && resp.getUsers() != null) {
                for (UserInfo u : resp.getUsers()) {
                    map.put(u.getId(), u);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("batch fetch users failed userIds={}", userIds, e);
            return Map.of();
        }
    }

    private FriendInfoDTO toFriendDto(Friend f, Map<Long, UserInfo> users, Map<Long, String> groupNames) {
        UserInfo u = users.get(f.getFriendId());
        long groupId = f.getGroupId();
        return new FriendInfoDTO(f.getFriendId(),
                u != null ? u.getUsername() : "未知用户",
                u != null ? u.getAvatar() : "",
                f.getRemark(),
                groupId,
                groupId == DEFAULT_GROUP_ID ? DEFAULT_GROUP_NAME
                        : groupNames.getOrDefault(groupId, DEFAULT_GROUP_NAME),
                STATUS_OFFLINE,
                millis(f.getCreatedAt()));
    }

    private FriendRequestDTO toRequestDto(FriendRequest row) {
        Map<Long, UserInfo> users = fetchUsers(List.of(row.getFromUserId(), row.getToUserId()));
        UserInfo from = users.get(row.getFromUserId());
        UserInfo to = users.get(row.getToUserId());
        return new FriendRequestDTO(row.getId(), row.getFromUserId(),
                from != null ? from.getUsername() : "未知用户",
                from != null ? from.getAvatar() : "",
                row.getToUserId(),
                to != null ? to.getUsername() : "未知用户",
                to != null ? to.getAvatar() : "",
                row.getMessage(), row.getStatus(),
                millis(row.getCreatedAt()), millis(row.getUpdatedAt()));
    }

    /** 好友数按分组统计（分组列表 friendCount 用） */
    private Map<Long, Integer> friendCountByGroup(long userId) {
        Map<Long, Integer> countBy = new HashMap<>();
        for (Friend f : friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, userId).select(Friend::getGroupId))) {
            countBy.merge(f.getGroupId(), 1, Integer::sum);
        }
        return countBy;
    }

    private Map<Long, String> groupNameMap(long userId) {
        Map<Long, String> map = new HashMap<>();
        for (FriendGroup g : groupMapper.selectList(new LambdaQueryWrapper<FriendGroup>()
                .eq(FriendGroup::getUserId, userId))) {
            map.put(g.getId(), g.getName());
        }
        return map;
    }

    private int clampPageNum(int pageNum) {
        return Math.max(1, pageNum);
    }

    private int clampPageSize(int pageSize, int defaultSize) {
        if (pageSize <= 0) {
            return defaultSize;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private long millis(OffsetDateTime time) {
        return time == null ? 0L : time.toInstant().toEpochMilli();
    }
}
