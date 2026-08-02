package lanshan.manmu.friend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

/**
 * FriendServiceImpl 单测：错误码分支 + 幂等/并发兜底路径（Mockito，无 Spring 上下文）。
 * <p>数据库交互全部 mock；端到端行为由 FriendIntegrationTest（Testcontainers）覆盖。
 */
class FriendServiceImplTest {

    private FriendMapper friendMapper;
    private FriendGroupMapper groupMapper;
    private FriendRequestMapper requestMapper;
    private UserBlockMapper blockMapper;
    private SnowflakeIdWorker snowflake;
    private UserRpcService userRpcService;
    private FriendServiceImpl service;

    private static final long USER_A = 1001L;
    private static final long USER_B = 1002L;

    @BeforeEach
    void setUp() {
        friendMapper = Mockito.mock(FriendMapper.class);
        groupMapper = Mockito.mock(FriendGroupMapper.class);
        requestMapper = Mockito.mock(FriendRequestMapper.class);
        blockMapper = Mockito.mock(UserBlockMapper.class);
        snowflake = Mockito.mock(SnowflakeIdWorker.class);
        userRpcService = Mockito.mock(UserRpcService.class);
        service = new FriendServiceImpl(friendMapper, groupMapper, requestMapper, blockMapper, snowflake, userRpcService);

        // 纯 Mockito 单测无 Spring 上下文：初始化 MyBatis-Plus TableInfo 缓存，
        // 否则 LambdaQueryWrapper/LambdaUpdateWrapper 解析列名时抛 "can not find lambda cache"
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Friend.class);
        TableInfoHelper.initTableInfo(assistant, FriendGroup.class);
        TableInfoHelper.initTableInfo(assistant, FriendRequest.class);
        TableInfoHelper.initTableInfo(assistant, UserBlock.class);

        when(snowflake.nextId()).thenReturn(9001L);
        // 默认：用户存在（batch 返回本人视图），无好友/无拉黑/无 pending
        when(userRpcService.batchGetUserInfo(any(BatchGetUserInfoReq.class))).thenAnswer(inv -> {
            BatchGetUserInfoReq req = inv.getArgument(0);
            List<UserInfo> users = req.getUserIds().stream()
                    .map(id -> new UserInfo(id, "u" + id, "", "", "", 0, "", 0L, 0L, 0L, BigDecimal.ZERO))
                    .toList();
            return new BatchGetUserInfoResp(users);
        });
        when(friendMapper.selectCount(any())).thenReturn(0L);
        when(blockMapper.selectCount(any())).thenReturn(0L);
        when(requestMapper.selectOne(any())).thenReturn(null);
    }

    // ==================== 发申请 ====================

    @Test
    void sendFriendRequest_targetNotExists_throws10001() {
        when(userRpcService.batchGetUserInfo(any(BatchGetUserInfoReq.class)))
                .thenReturn(new BatchGetUserInfoResp(List.of()));
        assertBizCode(() -> service.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "")),
                ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void sendFriendRequest_toSelf_throws20004() {
        assertBizCode(() -> service.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_A, "")),
                ErrorCode.FRIEND_SELF);
    }

    @Test
    void sendFriendRequest_alreadyFriends_throws20001() {
        when(friendMapper.selectCount(any())).thenReturn(1L);
        assertBizCode(() -> service.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "")),
                ErrorCode.FRIEND_ALREADY_EXISTS);
    }

    @Test
    void sendFriendRequest_blockedByMe_throws20006() {
        when(blockMapper.selectCount(any())).thenReturn(1L);
        assertBizCode(() -> service.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "")),
                ErrorCode.BLOCKED_BY_YOU);
    }

    @Test
    void sendFriendRequest_blockedByThem_throws20007() {
        // 方向：A→B 被拒需要 B→A 在黑名单；selectCount 第一次（A→B）=0，第二次（B→A）=1
        when(blockMapper.selectCount(any())).thenReturn(0L, 1L);
        assertBizCode(() -> service.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "")),
                ErrorCode.BLOCKED_BY_THEM);
    }

    @Test
    void sendFriendRequest_existingPending_refreshesAndReturnsSameId() {
        FriendRequest pending = new FriendRequest(5001L, USER_A, USER_B, "old", 1,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(requestMapper.selectOne(any())).thenReturn(pending);

        SendFriendRequestResp resp = service.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "new"));

        assertThat(resp.getRequestId()).isEqualTo(5001L);
        verify(requestMapper).updateById(pending);
        assertThat(pending.getMessage()).isEqualTo("new");
    }

    @Test
    void sendFriendRequest_duplicateKeyFallback_returnsExistingPending() {
        // 并发双申请：查无 → insert 撞唯一索引 → 重查返回既有 pending（幂等）
        when(requestMapper.selectOne(any())).thenReturn(null);
        Mockito.doThrow(new DuplicateKeyException("dup")).when(requestMapper).insert(any(FriendRequest.class));
        FriendRequest existing = new FriendRequest(5002L, USER_A, USER_B, "concurrent", 1,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(requestMapper.selectOne(any())).thenReturn(null, existing);

        SendFriendRequestResp resp = service.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "x"));

        assertThat(resp.getRequestId()).isEqualTo(5002L);
    }

    // ==================== 接受/拒绝/取消 ====================

    @Test
    void acceptFriendRequest_notPending_throws20002() {
        when(requestMapper.update(any(), any())).thenReturn(0);
        assertBizCode(() -> service.acceptFriendRequest(new AcceptFriendRequestReq(5001L, USER_B)),
                ErrorCode.FRIEND_REQUEST_HANDLED);
    }

    @Test
    void acceptFriendRequest_createsFriendPair() {
        // update（条件 UPDATE status=1→2）mock 生效后，selectById 返回 status=2 的记录
        FriendRequest row = new FriendRequest(5001L, USER_A, USER_B, "hi", 2,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(requestMapper.selectById(5001L)).thenReturn(row);

        FriendRequestDTO dto = service.acceptFriendRequest(new AcceptFriendRequestReq(5001L, USER_B));

        assertThat(dto.getStatus()).isEqualTo(2);
        // 双向建好友（insert ×2）
        verify(friendMapper, Mockito.times(2)).insert(any(Friend.class));
    }

    @Test
    void deleteFriend_notFriend_throws20003() {
        when(friendMapper.delete(any(Wrapper.class))).thenReturn(0);
        assertBizCode(() -> service.deleteFriend(new DeleteFriendReq(USER_A, USER_B)),
                ErrorCode.NOT_FRIEND);
    }

    @Test
    void moveGroup_groupNotExists_throws20005() {
        Friend friend = new Friend(1L, USER_A, USER_B, 0L, "", OffsetDateTime.now());
        when(friendMapper.selectOne(any())).thenReturn(friend);
        when(groupMapper.selectOne(any())).thenReturn(null);

        assertBizCode(() -> service.moveGroup(new MoveGroupReq(USER_A, USER_B, 8888L)),
                ErrorCode.FRIEND_GROUP_NOT_FOUND);
    }

    @Test
    void unblockUser_notBlocked_throws20008() {
        when(blockMapper.delete(any(Wrapper.class))).thenReturn(0);
        assertBizCode(() -> service.unblockUser(new UnblockUserReq(USER_A, USER_B)),
                ErrorCode.NOT_BLOCKED);
    }

    // ==================== 分页钳制 ====================

    @Test
    void listFriends_clampsPageSizeToDefaultOrMax() {
        // pageSize=0 → 默认 100；>100 → 钳到 100；pageNum<1 → 1
        service.listFriends(new ListFriendsReq(USER_A, null, 0, 0));
        ArgumentCaptor<Page<Friend>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(friendMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);

        service.listFriends(new ListFriendsReq(USER_A, null, 1, 1000));
        verify(friendMapper, Mockito.times(2)).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);
    }

    @Test
    void listFriendRequests_incomingFiltersPending() {
        // incoming 仅 status=1；pageSize=0 → 默认 50（契约 §4）
        service.listFriendRequests(new ListFriendRequestsReq(USER_B, "incoming", 1, 0));
        ArgumentCaptor<Page<FriendRequest>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(requestMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(50);
    }

    // ==================== 辅助 ====================

    private void assertBizCode(Runnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(errorCode.getCode()));
    }
}
