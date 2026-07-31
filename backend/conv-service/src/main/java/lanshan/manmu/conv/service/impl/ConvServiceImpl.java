package lanshan.manmu.conv.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.common.rpc.dto.conv.*;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoReq;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoResp;
import lanshan.manmu.common.rpc.dto.user.UserInfo;
import lanshan.manmu.common.util.SnowflakeIdWorker;
import lanshan.manmu.conv.event.ConvEventPublisher;
import lanshan.manmu.conv.event.MarkReadCompletedEvent;
import lanshan.manmu.conv.event.MembersJoinedEvent;
import lanshan.manmu.conv.event.MembersLeftEvent;
import lanshan.manmu.conv.mapper.ConvReadSeqMapper;
import lanshan.manmu.conv.mapper.ConvSettingsMapper;
import lanshan.manmu.conv.mapper.ConversationMapper;
import lanshan.manmu.conv.mapper.ConversationMemberMapper;
import lanshan.manmu.conv.model.entity.ConvReadSeq;
import lanshan.manmu.conv.model.entity.ConvSettings;
import lanshan.manmu.conv.model.entity.Conversation;
import lanshan.manmu.conv.model.entity.ConversationMember;
import lanshan.manmu.conv.service.ConvService;
import lanshan.manmu.conv.util.ConvConstants;
import lanshan.manmu.conv.util.PermissionChecker;
import lanshan.manmu.conv.util.UnreadCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ConvServiceImpl implements ConvService {

    private final ConversationMapper convMapper;
    private final ConversationMemberMapper memberMapper;
    private final ConvReadSeqMapper readSeqMapper;
    private final ConvSettingsMapper settingsMapper;
    private final SnowflakeIdWorker snowflake;
    private final PermissionChecker permissionChecker;
    private final UnreadCacheService unreadCache;
    private final ConvEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @DubboReference
    private UserRpcService userRpcService;

    public ConvServiceImpl(ConversationMapper convMapper,
                           ConversationMemberMapper memberMapper,
                           ConvReadSeqMapper readSeqMapper,
                           ConvSettingsMapper settingsMapper,
                           SnowflakeIdWorker snowflake,
                           PermissionChecker permissionChecker,
                           UnreadCacheService unreadCache,
                           ConvEventPublisher eventPublisher,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.convMapper = convMapper;
        this.memberMapper = memberMapper;
        this.readSeqMapper = readSeqMapper;
        this.settingsMapper = settingsMapper;
        this.snowflake = snowflake;
        this.permissionChecker = permissionChecker;
        this.unreadCache = unreadCache;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /** 在事务内发布 Spring 内部事件，由 @TransactionalEventListener 在 AFTER_COMMIT 处理 */
    private void publishAfterCommit(Object springEvent) {
        applicationEventPublisher.publishEvent(springEvent);
    }

    // ==================== Phase 1.2 写路径 ====================

    @Override
    @Transactional
    public CreateConversationResp createConversation(CreateConversationReq req) {
        int type = req.getType();
        long creatorId = req.getCreatorId();

        if (type == ConvType.SINGLE) {
            return createSingleConversation(req, creatorId);
        } else if (type == ConvType.GROUP) {
            return createGroupConversation(req, creatorId);
        } else {
            throw new BizException(ErrorCode.BAD_REQUEST, "invalid conv type: " + type);
        }
    }

    private CreateConversationResp createSingleConversation(CreateConversationReq req, long creatorId) {
        Long peerUserId = req.getPeerUserId();
        if (peerUserId == null || peerUserId <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "single conv requires peerUserId");
        }
        if (peerUserId == creatorId) {
            throw new BizException(ErrorCode.BAD_REQUEST, "cannot create single conv with self");
        }
        // 单聊去重
        Conversation existing = convMapper.findPrivateConversation(creatorId, peerUserId);
        if (existing != null) {
            return new CreateConversationResp(existing.getId(), toDto(existing));
        }
        long convId = snowflake.nextId();
        Conversation conv = newConversation(convId, ConvType.SINGLE, "", "", 0L, 2);
        convMapper.insert(conv);
        addMemberRecord(convId, creatorId, MemberRole.MEMBER);
        addMemberRecord(convId, peerUserId, MemberRole.MEMBER);
        return new CreateConversationResp(convId, toDto(conv));
    }

    private CreateConversationResp createGroupConversation(CreateConversationReq req, long creatorId) {
        String name = req.getName();
        if (name == null || name.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "group conv requires name");
        }
        if (name.length() > ConvConstants.MAX_NAME_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "name too long");
        }
        List<Long> memberIds = req.getMemberIds() == null ? List.of() : req.getMemberIds();
        int memberCount = 1 + memberIds.size();
        if (memberCount > ConvConstants.MAX_MEMBER_COUNT) {
            throw new BizException(ErrorCode.CONV_MEMBER_LIMIT, "exceed max member count");
        }
        long convId = snowflake.nextId();
        String avatar = req.getAvatar() == null ? "" : req.getAvatar();
        Conversation conv = newConversation(convId, ConvType.GROUP, name, avatar, creatorId, memberCount);
        convMapper.insert(conv);
        addMemberRecord(convId, creatorId, MemberRole.OWNER);
        for (Long memberId : memberIds) {
            addMemberRecord(convId, memberId, MemberRole.MEMBER);
        }
        return new CreateConversationResp(convId, toDto(conv));
    }

    @Override
    @Transactional
    public AddMembersResp addMembers(AddMembersReq req) {
        long convId = req.getConversationId();
        long operatorId = req.getOperatorId();
        permissionChecker.requireAdmin(convId, operatorId);

        Conversation conv = convMapper.selectById(convId);
        if (conv == null) {
            throw new BizException(ErrorCode.CONV_NOT_FOUND, "conv " + convId);
        }

        List<Long> userIds = req.getUserIds() == null ? List.of() : req.getUserIds();
        List<Long> addedUserIds = new ArrayList<>();
        List<Long> alreadyMemberIds = new ArrayList<>();

        for (Long userId : userIds) {
            if (permissionChecker.getMember(convId, userId) != null) {
                alreadyMemberIds.add(userId);
                continue;
            }
            if (conv.getMemberCount() + addedUserIds.size() + 1 > ConvConstants.MAX_MEMBER_COUNT) {
                throw new BizException(ErrorCode.CONV_MEMBER_LIMIT, "exceed max member count");
            }
            addMemberRecord(convId, userId, MemberRole.MEMBER);
            addedUserIds.add(userId);
        }

        if (!addedUserIds.isEmpty()) {
            conv.setMemberCount(conv.getMemberCount() + addedUserIds.size());
            convMapper.updateById(conv);
            publishAfterCommit(new MembersJoinedEvent(convId, addedUserIds, operatorId));
        }

        return new AddMembersResp(addedUserIds, alreadyMemberIds);
    }

    @Override
    @Transactional
    public void removeMembers(RemoveMembersReq req) {
        long convId = req.getConversationId();
        long operatorId = req.getOperatorId();
        List<Long> userIds = req.getUserIds() == null ? List.of() : req.getUserIds();

        List<Long> removedUserIds = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId != operatorId) {
                permissionChecker.requireAdmin(convId, operatorId);
                permissionChecker.verifyTargetNotHigher(convId, userId, operatorId);
            }
            ConversationMember member = permissionChecker.getMember(convId, userId);
            if (member == null) {
                continue;
            }
            memberMapper.deleteById(member.getId());
            removedUserIds.add(userId);
        }

        if (!removedUserIds.isEmpty()) {
            Conversation conv = convMapper.selectById(convId);
            if (conv != null) {
                conv.setMemberCount(Math.max(0, conv.getMemberCount() - removedUserIds.size()));
                convMapper.updateById(conv);
            }
            publishAfterCommit(new MembersLeftEvent(convId, removedUserIds, operatorId));
        }
    }

    @Override
    @Transactional
    public void muteMember(MuteMemberReq req) {
        long convId = req.getConversationId();
        long operatorId = req.getOperatorId();
        permissionChecker.requireAdmin(convId, operatorId);
        permissionChecker.verifyTargetNotHigher(convId, req.getTargetUserId(), operatorId);

        ConversationMember member = permissionChecker.getMember(convId, req.getTargetUserId());
        if (member == null) {
            throw new BizException(ErrorCode.CONV_MEMBER_NOT_FOUND, "target " + req.getTargetUserId());
        }
        member.setIsMuted(true);
        member.setMuteUntil(req.getMuteUntil());
        memberMapper.updateById(member);
    }

    @Override
    @Transactional
    public void unmuteMember(MuteMemberReq req) {
        long convId = req.getConversationId();
        long operatorId = req.getOperatorId();
        permissionChecker.requireAdmin(convId, operatorId);
        permissionChecker.verifyTargetNotHigher(convId, req.getTargetUserId(), operatorId);

        ConversationMember member = permissionChecker.getMember(convId, req.getTargetUserId());
        if (member == null) {
            throw new BizException(ErrorCode.CONV_MEMBER_NOT_FOUND, "target " + req.getTargetUserId());
        }
        // 解除禁言：写入 is_muted=false / mute_until=0。
        // 注意：永久禁言(muteMember)写入 is_muted=true / mute_until=0，二者仅 isMuted 不同，
        // 必须用独立方法而非复用 muteMember，否则消息拦截层会把 isMuted=true+muteUntil=0 判为永久禁言，
        // 导致被禁言用户永远无法解除（功能 bug 修复）。
        member.setIsMuted(false);
        member.setMuteUntil(0L);
        memberMapper.updateById(member);
    }

    @Override
    @Transactional
    public void transferOwner(TransferOwnerReq req) {
        long convId = req.getConversationId();
        long fromUserId = req.getFromUserId();
        long toUserId = req.getToUserId();

        permissionChecker.requireOwner(convId, fromUserId);

        if (fromUserId == toUserId) {
            throw new BizException(ErrorCode.CONV_OWNER_TRANSFER_SELF, "from==to");
        }

        ConversationMember target = permissionChecker.getMember(convId, toUserId);
        if (target == null) {
            throw new BizException(ErrorCode.CONV_MEMBER_NOT_FOUND, "target " + toUserId);
        }

        Conversation conv = convMapper.selectById(convId);
        if (conv == null) {
            throw new BizException(ErrorCode.CONV_NOT_FOUND, "conv " + convId);
        }

        // 三步原子：更新 conv.ownerId → 新群主 OWNER → 旧群主 MEMBER
        conv.setOwnerId(toUserId);
        convMapper.updateById(conv);

        target.setRole(MemberRole.OWNER);
        memberMapper.updateById(target);

        ConversationMember oldOwner = permissionChecker.getMember(convId, fromUserId);
        if (oldOwner != null) {
            oldOwner.setRole(MemberRole.MEMBER);
            memberMapper.updateById(oldOwner);
        }
    }

    @Override
    @Transactional
    public void updateAnnouncement(long convId, long operatorId, String content) {
        permissionChecker.requireAdmin(convId, operatorId);
        if (content != null && content.length() > ConvConstants.MAX_ANNOUNCEMENT_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "announcement too long");
        }
        Conversation conv = convMapper.selectById(convId);
        if (conv == null) {
            throw new BizException(ErrorCode.CONV_NOT_FOUND, "conv " + convId);
        }
        conv.setAnnouncement(content == null ? "" : content);
        convMapper.updateById(conv);
    }

    // ==================== Phase 1.3 读路径 ====================

    @Override
    public ConversationDTO getConversation(long conversationId, long userId) {
        // 先查会话存在性
        Conversation conv = convMapper.selectById(conversationId);
        if (conv == null) {
            throw new BizException(ErrorCode.CONV_NOT_FOUND, "conv " + conversationId);
        }
        // 读权限校验：调用者必须是会话成员（非成员抛 CONV_NOT_MEMBER 30004），
        // 防止任意登录用户按 convId 遍历窃取会话信息（数据泄露漏洞修复）
        permissionChecker.requireMember(conversationId, userId);
        ConversationDTO dto = toDto(conv);
        dto.setUnreadCount(unreadCache.getUnreadCount(userId, conversationId));
        return dto;
    }

    @Override
    public ListConversationsResp listConversations(ListConversationsReq req) {
        long userId = req.getUserId();
        int pageNum = req.getPageNum() <= 0 ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() <= 0 ? 20 : Math.min(req.getPageSize(), 100);

        Page<Conversation> page = new Page<>(pageNum, pageSize);
        IPage<Conversation> result = convMapper.listUserConversations(page, userId);

        List<ConversationDTO> dtos = result.getRecords().stream().map(this::toDto).toList();

        // 批量查未读数
        if (!dtos.isEmpty()) {
            List<Long> convIds = dtos.stream().map(ConversationDTO::getId).toList();
            Map<Long, Long> unreadMap = unreadCache.batchGetUnread(userId, convIds);
            dtos.forEach(dto -> dto.setUnreadCount(unreadMap.getOrDefault(dto.getId(), 0L)));
        }

        return new ListConversationsResp(dtos, result.getTotal());
    }

    @Override
    public GetMembersResp getMembers(GetMembersReq req) {
        long convId = req.getConversationId();
        long userId = req.getUserId();

        // 读权限校验：先确认会话存在，再校验调用者是成员。
        // 原实现完全忽略 req.getUserId()，导致非成员可枚举他人会话成员列表（数据泄露漏洞修复）。
        // 先查会话存在性避免"会话不存在"被误报成"非成员"。
        if (convMapper.selectById(convId) == null) {
            throw new BizException(ErrorCode.CONV_NOT_FOUND, "conv " + convId);
        }
        permissionChecker.requireMember(convId, userId);

        int pageNum = req.getPageNum() <= 0 ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() <= 0 ? 20 : Math.min(req.getPageSize(), 100);

        // 查会话成员分页（按 role ASC：OWNER 在前，ADMIN 次之，MEMBER 最后）
        Page<ConversationMember> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ConversationMember> wrapper = new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConvId, convId)
                .orderByAsc(ConversationMember::getRole);
        IPage<ConversationMember> result = memberMapper.selectPage(page, wrapper);

        List<ConversationMember> members = result.getRecords();
        if (members.isEmpty()) {
            return new GetMembersResp(List.of(), 0L);
        }

        // 批量查 lastReadSeq
        List<Long> userIds = members.stream().map(ConversationMember::getUserId).toList();
        List<ConvReadSeq> readSeqs = readSeqMapper.selectList(new LambdaQueryWrapper<ConvReadSeq>()
                .eq(ConvReadSeq::getConvId, convId)
                .in(ConvReadSeq::getUserId, userIds));
        Map<Long, Long> readSeqMap = readSeqs.stream()
                .collect(Collectors.toMap(ConvReadSeq::getUserId, ConvReadSeq::getLastReadSeq, (a, b) -> a));

        // 批量查用户信息补全 username/avatar
        BatchGetUserInfoResp userResp = userRpcService.batchGetUserInfo(new BatchGetUserInfoReq(userIds));
        Map<Long, UserInfo> userMap = (userResp == null || userResp.getUsers() == null) ? Map.of() :
                userResp.getUsers().stream().collect(Collectors.toMap(UserInfo::getId, u -> u, (a, b) -> a));

        // 转 DTO
        List<ConversationMemberDTO> dtos = members.stream().map(m -> toMemberDto(m, readSeqMap, userMap)).toList();

        return new GetMembersResp(dtos, result.getTotal());
    }

    @Override
    public boolean isMember(long conversationId, long userId) {
        return permissionChecker.getMember(conversationId, userId) != null;
    }

    // ==================== Phase 1.4 消息链路 + 事务后置 ====================

    @Override
    public PreCheckSendResp preCheckSend(PreCheckSendReq req) {
        long convId = req.getConversationId();
        long userId = req.getUserId();

        // 关键：查成员身份
        ConversationMember member = permissionChecker.getMember(convId, userId);
        boolean isMember = member != null;

        // 非关键：查 conv 拿 convType/isMutedAll（错误只 log，不抛）
        int convType = 0;
        boolean isMutedAll = false;
        try {
            Conversation conv = convMapper.selectById(convId);
            if (conv != null) {
                convType = conv.getType();
                isMutedAll = Boolean.TRUE.equals(conv.getIsMutedAll());
            }
        } catch (Exception e) {
            log.warn("preCheckSend query conv failed convId={}", convId, e);
        }

        // 填 isMuted/muteUntil（非成员时默认 false/0）
        boolean isMuted = isMember && Boolean.TRUE.equals(member.getIsMuted());
        long muteUntil = (isMember && member.getMuteUntil() != null) ? member.getMuteUntil() : 0L;

        // 查全量 memberIds 供 message-service 扇出
        List<Long> memberIds = listMemberIds(convId);

        // 信息收集型，不做拦截决策（决策 14）
        return new PreCheckSendResp(isMember, isMuted, isMutedAll, muteUntil, convType, memberIds);
    }

    @Override
    @Transactional
    public void updateLastMessage(UpdateLastMessageReq req) {
        long convId = req.getConversationId();
        Conversation conv = convMapper.selectById(convId);
        if (conv == null) {
            // 不存在仅 WARN 不抛（spec 第 12.2 节），return
            log.warn("updateLastMessage conv not found convId={}", convId);
            return;
        }
        // 幂等更新：WHERE max_seq < #{seq} 保证只增不减
        int rows = convMapper.updateLastMessageSeq(convId, req.getLastMessageId(),
                req.getMaxSeq(), req.getLastMessagePreview());
        log.debug("updateLastMessage convId={} seq={} affectedRows={}", convId, req.getMaxSeq(), rows);
    }

    @Override
    @Transactional
    public void markRead(MarkReadReq req) {
        long convId = req.getConversationId();
        long userId = req.getUserId();
        long lastReadSeq = req.getLastReadSeq();

        // 读权限校验：先确认会话存在，再校验调用者是成员，在 UPSERT 前拦截非成员越权标记已读。
        // 先查会话存在性避免"会话不存在"被误报成"非成员"。
        if (convMapper.selectById(convId) == null) {
            throw new BizException(ErrorCode.CONV_NOT_FOUND, "conv " + convId);
        }
        permissionChecker.requireMember(convId, userId);

        // UPSERT 已读位置（GREATEST 保证只增不减，spec 第 8.3 节）
        readSeqMapper.upsertReadSeq(snowflake.nextId(), convId, userId, lastReadSeq);

        // 事务后置：Redis DEL + Kafka 发事件，由 ConvEventListener 在 AFTER_COMMIT 执行
        // 不在事务内调 unreadCache.clearUnreadCount 和 eventPublisher.publishReadUpdated
        publishAfterCommit(new MarkReadCompletedEvent(userId, convId, lastReadSeq));
    }

    @Override
    public GetSettingsResp getSettings(GetSettingsReq req) {
        long convId = req.getConversationId();
        long userId = req.getUserId();

        ConvSettings settings = settingsMapper.selectOne(new LambdaQueryWrapper<ConvSettings>()
                .eq(ConvSettings::getConvId, convId)
                .eq(ConvSettings::getUserId, userId));

        boolean isMuted = settings != null && Boolean.TRUE.equals(settings.getIsMuted());
        boolean isPinned = settings != null && Boolean.TRUE.equals(settings.getIsPinned());

        // nickname 来自 conv_members.alias（用户在该会话的备注名），无设置则空字符串
        String nickname = "";
        ConversationMember member = permissionChecker.getMember(convId, userId);
        if (member != null && member.getAlias() != null) {
            nickname = member.getAlias();
        }

        return new GetSettingsResp(isMuted, isPinned, nickname);
    }

    @Override
    @Transactional
    public void updateSettings(UpdateSettingsReq req) {
        // UPSERT（COALESCE 处理 null=不更新语义，spec 第 8.4 节）
        settingsMapper.upsertSettings(snowflake.nextId(), req.getConversationId(),
                req.getUserId(), req.getIsMuted(), req.getIsPinned());

        // nickname 存 conv_members.alias（如非空则更新）
        if (req.getNickname() != null && !req.getNickname().isEmpty()) {
            ConversationMember member = permissionChecker.getMember(req.getConversationId(), req.getUserId());
            if (member != null) {
                member.setAlias(req.getNickname());
                memberMapper.updateById(member);
            }
        }
    }

    // ==================== 私有工具方法 ====================

    /** 查会话全量成员 userId（供 message-service 扇出） */
    private List<Long> listMemberIds(long convId) {
        List<ConversationMember> members = memberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConvId, convId));
        return members.stream().map(ConversationMember::getUserId).toList();
    }

    private Conversation newConversation(long convId, int type, String name, String avatar,
                                         long ownerId, int memberCount) {
        Conversation conv = new Conversation();
        conv.setId(convId);
        conv.setType(type);
        conv.setName(name);
        conv.setAvatar(avatar);
        conv.setOwnerId(ownerId);
        conv.setAnnouncement("");
        conv.setIsMutedAll(false);
        conv.setBackground("");
        conv.setMaxSeq(0L);
        conv.setLastMessageId(0L);
        conv.setLastMessagePreview("");
        conv.setMemberCount(memberCount);
        return conv;
    }

    private void addMemberRecord(long convId, long userId, int role) {
        ConversationMember member = new ConversationMember();
        member.setId(snowflake.nextId());
        member.setConvId(convId);
        member.setUserId(userId);
        member.setMemberType(ConvConstants.MEMBER_TYPE_USER);
        member.setBotId(0L);
        member.setRole(role);
        member.setAlias("");
        member.setIsMuted(false);
        member.setMuteUntil(0L);
        memberMapper.insert(member);
    }

    private ConversationDTO toDto(Conversation conv) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conv.getId());
        dto.setType(conv.getType());
        dto.setName(conv.getName());
        dto.setAvatar(conv.getAvatar());
        dto.setOwnerId(conv.getOwnerId());
        dto.setMemberCount(conv.getMemberCount());
        dto.setMaxSeq(conv.getMaxSeq());
        dto.setLastMessageId(conv.getLastMessageId());
        dto.setLastMessagePreview(conv.getLastMessagePreview());
        dto.setAnnouncement(conv.getAnnouncement());
        dto.setMutedAll(conv.getIsMutedAll());
        dto.setCreatedAt(conv.getCreatedAt() == null ? 0L : conv.getCreatedAt().toInstant().toEpochMilli());
        dto.setUpdatedAt(conv.getUpdatedAt() == null ? 0L : conv.getUpdatedAt().toInstant().toEpochMilli());
        // unreadCount 由调用方（getConversation/listConversations）单独 set
        return dto;
    }

    private ConversationMemberDTO toMemberDto(ConversationMember m, Map<Long, Long> readSeqMap,
                                                Map<Long, UserInfo> userMap) {
        ConversationMemberDTO dto = new ConversationMemberDTO();
        dto.setUserId(m.getUserId());
        UserInfo user = userMap.get(m.getUserId());
        dto.setUsername(user == null ? "" : user.getUsername());
        dto.setAvatar(user == null ? "" : user.getAvatar());
        dto.setRole(m.getRole());
        dto.setAlias(m.getAlias() == null ? "" : m.getAlias());
        dto.setJoinedAt(m.getJoinedAt() == null ? 0L : m.getJoinedAt().toInstant().toEpochMilli());
        dto.setLastReadSeq(readSeqMap.getOrDefault(m.getUserId(), 0L));
        dto.setMuted(Boolean.TRUE.equals(m.getIsMuted()));
        dto.setMuteUntil(m.getMuteUntil() == null ? 0L : m.getMuteUntil());
        dto.setMemberType(toDtoType(m.getMemberType()));
        dto.setBotId(m.getBotId() == null ? 0L : m.getBotId());
        return dto;
    }

    /** memberType DB('user'/'bot') → DTO(1/2)，spec 第 17 节 */
    private static int toDtoType(String dbType) {
        return ConvConstants.MEMBER_TYPE_BOT.equals(dbType) ? 2 : 1;
    }

    /** memberType DTO(1/2) → DB('user'/'bot')，spec 第 17 节 */
    @SuppressWarnings("unused")
    private static String toDbType(int dtoType) {
        return dtoType == 2 ? ConvConstants.MEMBER_TYPE_BOT : ConvConstants.MEMBER_TYPE_USER;
    }
}
