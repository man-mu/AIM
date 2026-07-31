package lanshan.manmu.conv.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.common.rpc.dto.conv.*;
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
import lanshan.manmu.conv.util.ConvConstants;
import lanshan.manmu.conv.util.PermissionChecker;
import lanshan.manmu.conv.util.UnreadCacheService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ConvServiceImpl 纯 Mockito 单测（spec 第 18.1 节，Phase 1.2 写路径部分）。
 * <p>覆盖 createConversation/addMembers/removeMembers/muteMember/transferOwner/updateAnnouncement。
 * <p>事务后置验证：事务内只 publish Spring 内部事件（applicationEventPublisher.publishEvent），
 * 不直接调用外部系统（eventPublisher.publishMemberJoined 等），后者由
 * @TransactionalEventListener(AFTER_COMMIT) 触发，单测无真实事务故不会执行。
 */
class ConvServiceImplTest {

    private ConversationMapper convMapper;
    private ConversationMemberMapper memberMapper;
    private ConvReadSeqMapper readSeqMapper;
    private ConvSettingsMapper settingsMapper;
    private SnowflakeIdWorker snowflake;
    private PermissionChecker permissionChecker;
    private UnreadCacheService unreadCache;
    private ConvEventPublisher eventPublisher;
    private ApplicationEventPublisher applicationEventPublisher;
    private UserRpcService userRpcService;

    private ConvServiceImpl convService;

    private static final long CONV_ID  = 1001L;
    private static final long CREATOR  = 2001L;
    private static final long PEER     = 2002L;
    private static final long MEMBER_A = 2003L;
    private static final long MEMBER_B = 2004L;

    /**
     * 纯 Mockito 环境下 MyBatis-Plus 未启动，LambdaQueryWrapper 的 .eq()/.in() 会
     * 解析 lambda → 字段名，需要 TableInfo 缓存。手动初始化所有 conv 实体的 TableInfo。
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, lanshan.manmu.conv.model.entity.Conversation.class);
        TableInfoHelper.initTableInfo(assistant, ConversationMember.class);
        TableInfoHelper.initTableInfo(assistant, ConvReadSeq.class);
        TableInfoHelper.initTableInfo(assistant, lanshan.manmu.conv.model.entity.ConvSettings.class);
    }

    @BeforeEach
    void setUp() {
        convMapper = Mockito.mock(ConversationMapper.class);
        memberMapper = Mockito.mock(ConversationMemberMapper.class);
        readSeqMapper = Mockito.mock(ConvReadSeqMapper.class);
        settingsMapper = Mockito.mock(ConvSettingsMapper.class);
        snowflake = Mockito.mock(SnowflakeIdWorker.class);
        permissionChecker = Mockito.mock(PermissionChecker.class);
        unreadCache = Mockito.mock(UnreadCacheService.class);
        eventPublisher = Mockito.mock(ConvEventPublisher.class);
        applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        userRpcService = Mockito.mock(UserRpcService.class);

        // snowflake 默认返回递增 ID
        when(snowflake.nextId()).thenReturn(9001L, 9002L, 9003L, 9004L, 9005L, 9006L);

        convService = new ConvServiceImpl(convMapper, memberMapper, readSeqMapper,
                settingsMapper, snowflake, permissionChecker, unreadCache,
                eventPublisher, applicationEventPublisher);
        // @DubboReference 字段注入：手动 set
        ReflectionTestUtils.setField(convService, "userRpcService", userRpcService);
    }

    private Conversation mockConv(long convId, int type, long ownerId, int memberCount) {
        Conversation c = new Conversation();
        c.setId(convId);
        c.setType(type);
        c.setName("");
        c.setAvatar("");
        c.setOwnerId(ownerId);
        c.setAnnouncement("");
        c.setIsMutedAll(false);
        c.setBackground("");
        c.setMaxSeq(0L);
        c.setLastMessageId(0L);
        c.setLastMessagePreview("");
        c.setMemberCount(memberCount);
        return c;
    }

    private ConversationMember mockMember(long convId, long userId, int role) {
        ConversationMember m = new ConversationMember();
        m.setId(System.nanoTime());
        m.setConvId(convId);
        m.setUserId(userId);
        m.setMemberType(ConvConstants.MEMBER_TYPE_USER);
        m.setRole(role);
        m.setIsMuted(false);
        m.setMuteUntil(0L);
        return m;
    }

    // ==================== createConversation ====================

    @Test
    void createConversation_invalidType_throwsBadRequest() {
        CreateConversationReq req = new CreateConversationReq(0, CREATOR, null, "", "", null);
        BizException ex = assertThrows(BizException.class,
                () -> convService.createConversation(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void createConversation_singleWithSelf_throwsBadRequest() {
        CreateConversationReq req = new CreateConversationReq(ConvType.SINGLE, CREATOR, CREATOR, "", "", null);
        BizException ex = assertThrows(BizException.class,
                () -> convService.createConversation(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void createConversation_singleNoPeer_throwsBadRequest() {
        CreateConversationReq req = new CreateConversationReq(ConvType.SINGLE, CREATOR, null, "", "", null);
        BizException ex = assertThrows(BizException.class,
                () -> convService.createConversation(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void createConversation_singleDedupHit_returnsExisting() {
        Conversation existing = mockConv(CONV_ID, ConvType.SINGLE, 0L, 2);
        when(convMapper.findPrivateConversation(CREATOR, PEER)).thenReturn(existing);

        CreateConversationReq req = new CreateConversationReq(ConvType.SINGLE, CREATOR, PEER, "", "", null);
        CreateConversationResp resp = convService.createConversation(req);

        assertEquals(CONV_ID, resp.getConversationId());
        assertNotNull(resp.getConversation());
        assertEquals(ConvType.SINGLE, resp.getConversation().getType());
        // 命中去重后不应该再 insert
        verify(convMapper, never()).insert(any(Conversation.class));
        verify(memberMapper, never()).insert(any(ConversationMember.class));
    }

    @Test
    void createConversation_singleNew_ownerZeroMemberCountTwo() {
        when(convMapper.findPrivateConversation(CREATOR, PEER)).thenReturn(null);

        CreateConversationReq req = new CreateConversationReq(ConvType.SINGLE, CREATOR, PEER, "", "", null);
        CreateConversationResp resp = convService.createConversation(req);

        // 验证 insert 1 个 conversation
        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(convMapper).insert(convCaptor.capture());
        Conversation inserted = convCaptor.getValue();
        assertEquals(ConvType.SINGLE, inserted.getType());
        assertEquals(0L, inserted.getOwnerId());
        assertEquals(2, inserted.getMemberCount());

        // 验证 insert 2 个 member（creator + peer），都是 MEMBER
        ArgumentCaptor<ConversationMember> memberCaptor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper, times(2)).insert(memberCaptor.capture());
        List<ConversationMember> members = memberCaptor.getAllValues();
        assertEquals(MemberRole.MEMBER, members.get(0).getRole());
        assertEquals(MemberRole.MEMBER, members.get(1).getRole());

        // 单聊不发事件（决策 20）
        verify(applicationEventPublisher, never()).publishEvent(any());
        assertEquals(9001L, resp.getConversationId());
    }

    @Test
    void createConversation_groupNoName_throwsBadRequest() {
        CreateConversationReq req = new CreateConversationReq(ConvType.GROUP, CREATOR, null, "", "", List.of());
        BizException ex = assertThrows(BizException.class,
                () -> convService.createConversation(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void createConversation_groupNameTooLong_throwsBadRequest() {
        String longName = "x".repeat(ConvConstants.MAX_NAME_LENGTH + 1);
        CreateConversationReq req = new CreateConversationReq(ConvType.GROUP, CREATOR, null, longName, "", List.of());
        BizException ex = assertThrows(BizException.class,
                () -> convService.createConversation(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void createConversation_groupExceedMemberLimit_throwsConvMemberLimit() {
        // 构造 500 个 memberIds（creator + 500 = 501 > 500）
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 1; i <= ConvConstants.MAX_MEMBER_COUNT; i++) ids.add(i);
        CreateConversationReq req = new CreateConversationReq(ConvType.GROUP, CREATOR, null, "g", "", ids);
        BizException ex = assertThrows(BizException.class,
                () -> convService.createConversation(req));
        assertEquals(ErrorCode.CONV_MEMBER_LIMIT.getCode(), ex.getCode());
    }

    @Test
    void createConversation_groupNew_ownerIsCreatorMemberCountCorrect() {
        List<Long> memberIds = List.of(MEMBER_A, MEMBER_B);
        CreateConversationReq req = new CreateConversationReq(ConvType.GROUP, CREATOR, null, "群聊", "avatar", memberIds);
        CreateConversationResp resp = convService.createConversation(req);

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(convMapper).insert(convCaptor.capture());
        Conversation inserted = convCaptor.getValue();
        assertEquals(ConvType.GROUP, inserted.getType());
        assertEquals(CREATOR, inserted.getOwnerId());
        assertEquals(3, inserted.getMemberCount()); // creator + 2

        // creator=OWNER, 其他=MEMBER
        ArgumentCaptor<ConversationMember> memberCaptor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper, times(3)).insert(memberCaptor.capture());
        List<ConversationMember> members = memberCaptor.getAllValues();
        assertEquals(MemberRole.OWNER, members.get(0).getRole());
        assertEquals(CREATOR, members.get(0).getUserId());
        assertEquals(MemberRole.MEMBER, members.get(1).getRole());
        assertEquals(MemberRole.MEMBER, members.get(2).getRole());

        verify(applicationEventPublisher, never()).publishEvent(any());
        assertEquals(9001L, resp.getConversationId());
    }

    // ==================== addMembers ====================

    @Test
    void addMembers_operatorNotAdmin_throwsPermissionDenied() {
        // permissionChecker.requireAdmin 抛异常
        doThrow(new BizException(ErrorCode.CONV_PERMISSION_DENIED, "not admin"))
                .when(permissionChecker).requireAdmin(CONV_ID, CREATOR);

        AddMembersReq req = new AddMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A));
        BizException ex = assertThrows(BizException.class,
                () -> convService.addMembers(req));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void addMembers_convNotExist_throwsConvNotFound() {
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(null);
        when(convMapper.selectById(CONV_ID)).thenReturn(null);

        AddMembersReq req = new AddMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A));
        BizException ex = assertThrows(BizException.class,
                () -> convService.addMembers(req));
        assertEquals(ErrorCode.CONV_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void addMembers_alreadyMember_skipInsert() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 1);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER));

        AddMembersReq req = new AddMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A));
        AddMembersResp resp = convService.addMembers(req);

        assertTrue(resp.getAddedUserIds().isEmpty());
        assertEquals(List.of(MEMBER_A), resp.getAlreadyMemberIds());
        verify(memberMapper, never()).insert(any(ConversationMember.class));
        verify(convMapper, never()).updateById(any(Conversation.class));
        // 没有新成员，不发事件
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void addMembers_exceedLimit_throwsConvMemberLimit() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, ConvConstants.MAX_MEMBER_COUNT);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(null);

        AddMembersReq req = new AddMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A));
        BizException ex = assertThrows(BizException.class,
                () -> convService.addMembers(req));
        assertEquals(ErrorCode.CONV_MEMBER_LIMIT.getCode(), ex.getCode());
    }

    @Test
    void addMembers_normal_memberCountIncrementAndPublishSpringEvent() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 2);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(null);
        when(permissionChecker.getMember(CONV_ID, MEMBER_B)).thenReturn(null);

        AddMembersReq req = new AddMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A, MEMBER_B));
        AddMembersResp resp = convService.addMembers(req);

        // 验证 insert 2 个 member
        verify(memberMapper, times(2)).insert(any(ConversationMember.class));
        // 验证 memberCount 更新为 4
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(convMapper).updateById(captor.capture());
        assertEquals(4, captor.getValue().getMemberCount());

        assertEquals(List.of(MEMBER_A, MEMBER_B), resp.getAddedUserIds());
        assertTrue(resp.getAlreadyMemberIds().isEmpty());

        // 事务后置验证：事务内只 publish Spring 内部事件，不直接调 eventPublisher（外部系统）
        ArgumentCaptor<Object> evtCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, times(1)).publishEvent(evtCaptor.capture());
        Object published = evtCaptor.getValue();
        assertInstanceOf(MembersJoinedEvent.class, published);
        MembersJoinedEvent mje = (MembersJoinedEvent) published;
        assertEquals(CONV_ID, mje.getConvId());
        assertEquals(List.of(MEMBER_A, MEMBER_B), mje.getUserIds());
        assertEquals(CREATOR, mje.getJoinedBy());

        // 外部系统 Kafka 调用不应该在事务内执行（应该由 AFTER_COMMIT listener 触发）
        verify(eventPublisher, never()).publishMemberJoined(anyLong(), anyList(), anyLong());
    }

    @Test
    void addMembers_emptyUserIds_noInsertNoEvent() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 2);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);

        AddMembersReq req = new AddMembersReq(CONV_ID, CREATOR, List.of());
        AddMembersResp resp = convService.addMembers(req);

        assertTrue(resp.getAddedUserIds().isEmpty());
        verify(memberMapper, never()).insert(any(ConversationMember.class));
        verify(convMapper, never()).updateById(any(Conversation.class));
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // ==================== removeMembers ====================

    @Test
    void removeMembers_selfQuit_bypassAdminCheck() {
        // operator==target==CREATOR 自退，permissionChecker.requireAdmin 不应被调用
        when(permissionChecker.getMember(CONV_ID, CREATOR))
                .thenReturn(mockMember(CONV_ID, CREATOR, MemberRole.MEMBER));
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);

        RemoveMembersReq req = new RemoveMembersReq(CONV_ID, CREATOR, List.of(CREATOR));
        convService.removeMembers(req);

        verify(permissionChecker, never()).requireAdmin(anyLong(), anyLong());
        verify(permissionChecker, never()).verifyTargetNotHigher(anyLong(), anyLong(), anyLong());
        verify(memberMapper).deleteById(anyLong());

        // 验证 memberCount 减 1
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(convMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getMemberCount());

        // 事务后置：publish MembersLeftEvent
        ArgumentCaptor<Object> evtCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(evtCaptor.capture());
        assertInstanceOf(MembersLeftEvent.class, evtCaptor.getValue());
        // 外部 Kafka 不在事务内调
        verify(eventPublisher, never()).publishMemberLeft(anyLong(), anyList(), anyLong());
    }

    @Test
    void removeMembers_nonAdmin_throwsPermissionDenied() {
        doThrow(new BizException(ErrorCode.CONV_PERMISSION_DENIED, "not admin"))
                .when(permissionChecker).requireAdmin(CONV_ID, CREATOR);

        RemoveMembersReq req = new RemoveMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A));
        BizException ex = assertThrows(BizException.class,
                () -> convService.removeMembers(req));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void removeMembers_targetSameRole_throwsPermissionDenied() {
        // 踢同级 → verifyTargetNotHigher 抛异常
        doThrow(new BizException(ErrorCode.CONV_PERMISSION_DENIED, "same role"))
                .when(permissionChecker).verifyTargetNotHigher(CONV_ID, MEMBER_A, CREATOR);

        RemoveMembersReq req = new RemoveMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A));
        BizException ex = assertThrows(BizException.class,
                () -> convService.removeMembers(req));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void removeMembers_targetNotMember_silentlySkip() {
        // target 不在 conv，getMember 返回 null，应该跳过不抛
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(null);
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);

        RemoveMembersReq req = new RemoveMembersReq(CONV_ID, CREATOR, List.of(MEMBER_A));
        convService.removeMembers(req);

        verify(memberMapper, never()).deleteById(anyLong());
        // 没有移除任何人 → 不更新 memberCount 不发事件
        verify(convMapper, never()).updateById(any(Conversation.class));
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // ==================== muteMember ====================

    @Test
    void muteMember_adminMuteMember_success() {
        ConversationMember target = mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(target);

        MuteMemberReq req = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 99999L);
        convService.muteMember(req);

        // 验证 requireAdmin + verifyTargetNotHigher 都被调用
        verify(permissionChecker).requireAdmin(CONV_ID, CREATOR);
        verify(permissionChecker).verifyTargetNotHigher(CONV_ID, MEMBER_A, CREATOR);

        // 验证 target 被更新
        ArgumentCaptor<ConversationMember> captor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper).updateById(captor.capture());
        ConversationMember updated = captor.getValue();
        assertTrue(updated.getIsMuted());
        assertEquals(99999L, updated.getMuteUntil());
    }

    @Test
    void muteMember_adminMuteAnotherAdmin_throwsPermissionDenied() {
        doThrow(new BizException(ErrorCode.CONV_PERMISSION_DENIED, "same role"))
                .when(permissionChecker).verifyTargetNotHigher(CONV_ID, MEMBER_A, CREATOR);

        MuteMemberReq req = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 99999L);
        BizException ex = assertThrows(BizException.class,
                () -> convService.muteMember(req));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void muteMember_targetNotExist_throwsMemberNotFound() {
        // 模拟 verifyTargetNotHigher 通过（target 存在），但 getMember 返回 null
        // 用于验证 NPE 防御性校验：getMember 返回 null 时 throw CONV_MEMBER_NOT_FOUND 而非 NPE
        doNothing().when(permissionChecker).verifyTargetNotHigher(CONV_ID, MEMBER_A, CREATOR);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(null);

        MuteMemberReq req = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 99999L);
        BizException ex = assertThrows(BizException.class,
                () -> convService.muteMember(req));
        assertEquals(ErrorCode.CONV_MEMBER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== unmuteMember（解除禁言，与永久禁言区分） ====================

    @Test
    void unmuteMember_adminUnmuteMember_setsNotMutedAndZeroUntil() {
        // target 当前处于永久禁言状态（isMuted=true, muteUntil=0）
        ConversationMember target = mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER);
        target.setIsMuted(true);
        target.setMuteUntil(0L);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(target);

        // DELETE /mute 端点 → unmuteMember，请求体 muteUntil=0（但 unmuteMember 忽略该值，强制 false/0）
        MuteMemberReq req = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 0L);
        convService.unmuteMember(req);

        // 验证权限校验
        verify(permissionChecker).requireAdmin(CONV_ID, CREATOR);
        verify(permissionChecker).verifyTargetNotHigher(CONV_ID, MEMBER_A, CREATOR);

        // 验证写库：isMuted=false / muteUntil=0（与永久禁言 isMuted=true/muteUntil=0 区分）
        ArgumentCaptor<ConversationMember> captor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper).updateById(captor.capture());
        ConversationMember updated = captor.getValue();
        assertFalse(updated.getIsMuted(), "解除禁言后应写 isMuted=false");
        assertEquals(0L, updated.getMuteUntil());
    }

    @Test
    void unmuteMember_nonAdmin_throwsPermissionDenied() {
        doThrow(new BizException(ErrorCode.CONV_PERMISSION_DENIED, "not admin"))
                .when(permissionChecker).requireAdmin(CONV_ID, CREATOR);

        MuteMemberReq req = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 0L);
        BizException ex = assertThrows(BizException.class,
                () -> convService.unmuteMember(req));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void unmuteMember_targetNotExist_throwsMemberNotFound() {
        doNothing().when(permissionChecker).verifyTargetNotHigher(CONV_ID, MEMBER_A, CREATOR);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(null);

        MuteMemberReq req = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 0L);
        BizException ex = assertThrows(BizException.class,
                () -> convService.unmuteMember(req));
        assertEquals(ErrorCode.CONV_MEMBER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void mute_permanent_thenUnmute_writeDistinctIsMuted() {
        // 回归：永久禁言(isMuted=true,muteUntil=0) 与解除禁言(isMuted=false,muteUntil=0) 写库不同
        // 确保不会因 muteUntil==0 无法区分来源而失效

        // 1) 永久禁言
        ConversationMember m = mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(m);

        MuteMemberReq muteReq = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 0L);
        convService.muteMember(muteReq);
        ArgumentCaptor<ConversationMember> muteCaptor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper, times(1)).updateById(muteCaptor.capture());
        assertTrue(muteCaptor.getValue().getIsMuted(), "永久禁言写 isMuted=true");
        assertEquals(0L, muteCaptor.getValue().getMuteUntil());

        // 2) 解除禁言
        // 重置 member 的 muted 状态模拟 DB 持久化后的状态
        m.setIsMuted(true);
        m.setMuteUntil(0L);

        MuteMemberReq unmuteReq = new MuteMemberReq(CONV_ID, CREATOR, MEMBER_A, 0L);
        convService.unmuteMember(unmuteReq);
        ArgumentCaptor<ConversationMember> unmuteCaptor = ArgumentCaptor.forClass(ConversationMember.class);
        verify(memberMapper, times(2)).updateById(unmuteCaptor.capture());
        ConversationMember unmuted = unmuteCaptor.getValue();
        assertFalse(unmuted.getIsMuted(), "解除禁言写 isMuted=false");
        assertEquals(0L, unmuted.getMuteUntil());
    }

    // ==================== transferOwner ====================

    @Test
    void transferOwner_notOwner_throwsPermissionDenied() {
        doThrow(new BizException(ErrorCode.CONV_PERMISSION_DENIED, "not owner"))
                .when(permissionChecker).requireOwner(CONV_ID, CREATOR);

        TransferOwnerReq req = new TransferOwnerReq(CONV_ID, CREATOR, MEMBER_A);
        BizException ex = assertThrows(BizException.class,
                () -> convService.transferOwner(req));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void transferOwner_toSelf_throwsOwnerTransferSelf() {
        TransferOwnerReq req = new TransferOwnerReq(CONV_ID, CREATOR, CREATOR);
        BizException ex = assertThrows(BizException.class,
                () -> convService.transferOwner(req));
        assertEquals(ErrorCode.CONV_OWNER_TRANSFER_SELF.getCode(), ex.getCode());
    }

    @Test
    void transferOwner_targetNotMember_throwsMemberNotFound() {
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(null);

        TransferOwnerReq req = new TransferOwnerReq(CONV_ID, CREATOR, MEMBER_A);
        BizException ex = assertThrows(BizException.class,
                () -> convService.transferOwner(req));
        assertEquals(ErrorCode.CONV_MEMBER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void transferOwner_convNotExist_throwsConvNotFound() {
        when(permissionChecker.getMember(CONV_ID, MEMBER_A))
                .thenReturn(mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER));
        when(convMapper.selectById(CONV_ID)).thenReturn(null);

        TransferOwnerReq req = new TransferOwnerReq(CONV_ID, CREATOR, MEMBER_A);
        BizException ex = assertThrows(BizException.class,
                () -> convService.transferOwner(req));
        assertEquals(ErrorCode.CONV_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void transferOwner_success_threeStepAtomic() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        ConversationMember target = mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER);
        ConversationMember oldOwner = mockMember(CONV_ID, CREATOR, MemberRole.OWNER);
        when(permissionChecker.getMember(CONV_ID, MEMBER_A)).thenReturn(target);
        when(permissionChecker.getMember(CONV_ID, CREATOR)).thenReturn(oldOwner);

        TransferOwnerReq req = new TransferOwnerReq(CONV_ID, CREATOR, MEMBER_A);
        convService.transferOwner(req);

        // 三步原子：1. conv.ownerId 更新为 target
        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(convMapper).updateById(convCaptor.capture());
        assertEquals(MEMBER_A, convCaptor.getValue().getOwnerId());

        // 2. target.role = OWNER
        assertEquals(MemberRole.OWNER, target.getRole());
        verify(memberMapper).updateById(target);

        // 3. oldOwner.role = MEMBER
        assertEquals(MemberRole.MEMBER, oldOwner.getRole());
        verify(memberMapper).updateById(oldOwner);

        // 不发 owner.transferred 事件（决策 20）
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // ==================== updateAnnouncement ====================

    @Test
    void updateAnnouncement_notAdmin_throwsPermissionDenied() {
        doThrow(new BizException(ErrorCode.CONV_PERMISSION_DENIED, "not admin"))
                .when(permissionChecker).requireAdmin(CONV_ID, CREATOR);

        BizException ex = assertThrows(BizException.class,
                () -> convService.updateAnnouncement(CONV_ID, CREATOR, "hi"));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void updateAnnouncement_tooLong_throwsBadRequest() {
        String longContent = "x".repeat(ConvConstants.MAX_ANNOUNCEMENT_LENGTH + 1);
        BizException ex = assertThrows(BizException.class,
                () -> convService.updateAnnouncement(CONV_ID, CREATOR, longContent));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void updateAnnouncement_convNotExist_throwsConvNotFound() {
        when(convMapper.selectById(CONV_ID)).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> convService.updateAnnouncement(CONV_ID, CREATOR, "hello"));
        assertEquals(ErrorCode.CONV_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateAnnouncement_success() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);

        convService.updateAnnouncement(CONV_ID, CREATOR, "新公告");

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(convMapper).updateById(captor.capture());
        assertEquals("新公告", captor.getValue().getAnnouncement());
    }

    @Test
    void updateAnnouncement_nullContent_setEmpty() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);

        convService.updateAnnouncement(CONV_ID, CREATOR, null);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(convMapper).updateById(captor.capture());
        assertEquals("", captor.getValue().getAnnouncement());
    }

    // ==================== Phase 1.3 读路径 ====================

    // ---- 读路径成员权限校验（数据泄露漏洞修复）----

    @Test
    void getConversation_convNotExist_throwsConvNotFound() {
        when(convMapper.selectById(CONV_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> convService.getConversation(CONV_ID, CREATOR));
        assertEquals(ErrorCode.CONV_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getConversation_nonMember_throwsConvNotMember() {
        // 会话存在但调用者非成员 → 抛 CONV_NOT_MEMBER（防止按 convId 遍历窃取会话信息）
        when(convMapper.selectById(CONV_ID))
                .thenReturn(mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3));
        // permissionChecker 是 mock：直接 stub requireMember 抛非成员异常，模拟非成员场景
        doThrow(new BizException(ErrorCode.CONV_NOT_MEMBER, "not in conv " + CONV_ID))
                .when(permissionChecker).requireMember(CONV_ID, CREATOR);

        BizException ex = assertThrows(BizException.class,
                () -> convService.getConversation(CONV_ID, CREATOR));
        assertEquals(ErrorCode.CONV_NOT_MEMBER.getCode(), ex.getCode());
        // 非成员不应再读未读数
        verify(unreadCache, never()).getUnreadCount(anyLong(), anyLong());
    }

    @Test
    void getMembers_nonMember_throwsConvNotMember() {
        // 会话存在但调用者非成员 → 抛 CONV_NOT_MEMBER（原实现忽略 userId 字段）
        when(convMapper.selectById(CONV_ID))
                .thenReturn(mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3));
        doThrow(new BizException(ErrorCode.CONV_NOT_MEMBER, "not in conv " + CONV_ID))
                .when(permissionChecker).requireMember(CONV_ID, CREATOR);

        GetMembersReq req = new GetMembersReq(CONV_ID, CREATOR, 1, 20);
        BizException ex = assertThrows(BizException.class,
                () -> convService.getMembers(req));
        assertEquals(ErrorCode.CONV_NOT_MEMBER.getCode(), ex.getCode());
        // 非成员不应分页查询成员
        verify(memberMapper, never()).selectPage(any(), any());
    }

    @Test
    void getMembers_convNotExist_throwsConvNotFound() {
        // 校验顺序：会话不存在优先于成员校验
        when(convMapper.selectById(CONV_ID)).thenReturn(null);

        GetMembersReq req = new GetMembersReq(CONV_ID, CREATOR, 1, 20);
        BizException ex = assertThrows(BizException.class,
                () -> convService.getMembers(req));
        assertEquals(ErrorCode.CONV_NOT_FOUND.getCode(), ex.getCode());
        verify(permissionChecker, never()).getMember(anyLong(), anyLong());
    }

    @Test
    void markRead_nonMember_throwsConvNotMember() {
        // 会话存在但调用者非成员 → UPSERT 前抛 CONV_NOT_MEMBER
        when(convMapper.selectById(CONV_ID))
                .thenReturn(mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3));
        doThrow(new BizException(ErrorCode.CONV_NOT_MEMBER, "not in conv " + CONV_ID))
                .when(permissionChecker).requireMember(CONV_ID, CREATOR);

        MarkReadReq req = new MarkReadReq(CREATOR, CONV_ID, 100L);
        BizException ex = assertThrows(BizException.class,
                () -> convService.markRead(req));
        assertEquals(ErrorCode.CONV_NOT_MEMBER.getCode(), ex.getCode());
        // 非成员不应 UPSERT 已读位置
        verify(readSeqMapper, never()).upsertReadSeq(anyLong(), anyLong(), anyLong(), anyLong());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void markRead_convNotExist_throwsConvNotFound() {
        // 校验顺序：会话不存在优先于成员校验
        when(convMapper.selectById(CONV_ID)).thenReturn(null);

        MarkReadReq req = new MarkReadReq(CREATOR, CONV_ID, 100L);
        BizException ex = assertThrows(BizException.class,
                () -> convService.markRead(req));
        assertEquals(ErrorCode.CONV_NOT_FOUND.getCode(), ex.getCode());
        verify(permissionChecker, never()).getMember(anyLong(), anyLong());
    }

    @Test
    void getConversation_success_unreadCountFilled() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        conv.setMaxSeq(100L);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        // 调用者是会话成员（requireMember 通过）
        when(permissionChecker.getMember(CONV_ID, CREATOR))
                .thenReturn(mockMember(CONV_ID, CREATOR, MemberRole.MEMBER));
        when(unreadCache.getUnreadCount(CREATOR, CONV_ID)).thenReturn(5L);

        ConversationDTO dto = convService.getConversation(CONV_ID, CREATOR);

        assertEquals(CONV_ID, dto.getId());
        assertEquals(ConvType.GROUP, dto.getType());
        assertEquals(CREATOR, dto.getOwnerId());
        assertEquals(3, dto.getMemberCount());
        assertEquals(100L, dto.getMaxSeq());
        assertEquals(5L, dto.getUnreadCount(), "unreadCount 应从 Redis 填充");
        verify(unreadCache).getUnreadCount(CREATOR, CONV_ID);
    }

    @Test
    void listConversations_empty_returnsEmptyList() {
        Page<Conversation> emptyPage = new Page<>(1, 20);
        emptyPage.setTotal(0);
        when(convMapper.listUserConversations(any(IPage.class), eq(CREATOR))).thenReturn(emptyPage);

        ListConversationsReq req = new ListConversationsReq(CREATOR, 1, 20);
        ListConversationsResp resp = convService.listConversations(req);

        assertNotNull(resp.getConversations());
        assertTrue(resp.getConversations().isEmpty());
        assertEquals(0L, resp.getTotal());
        // 空列表不查 Redis
        verify(unreadCache, never()).batchGetUnread(anyLong(), anyList());
    }

    @Test
    void listConversations_normal_batchFillUnreadCount() {
        Conversation c1 = mockConv(8001L, ConvType.GROUP, CREATOR, 3);
        c1.setMaxSeq(100L);
        Conversation c2 = mockConv(8002L, ConvType.SINGLE, 0L, 2);
        c2.setMaxSeq(50L);
        Page<Conversation> page = new Page<>(1, 20);
        page.setRecords(List.of(c1, c2));
        page.setTotal(2);
        when(convMapper.listUserConversations(any(IPage.class), eq(CREATOR))).thenReturn(page);
        Map<Long, Long> unreadMap = Map.of(8001L, 3L, 8002L, 0L);
        when(unreadCache.batchGetUnread(eq(CREATOR), anyList())).thenReturn(unreadMap);

        ListConversationsReq req = new ListConversationsReq(CREATOR, 1, 20);
        ListConversationsResp resp = convService.listConversations(req);

        assertEquals(2, resp.getConversations().size());
        assertEquals(2L, resp.getTotal());
        assertEquals(8001L, resp.getConversations().get(0).getId());
        assertEquals(3L, resp.getConversations().get(0).getUnreadCount());
        assertEquals(8002L, resp.getConversations().get(1).getId());
        assertEquals(0L, resp.getConversations().get(1).getUnreadCount());
    }

    @Test
    void listConversations_defaultPaging() {
        // pageNum/pageSize <= 0 时用默认值 1/20
        Page<Conversation> emptyPage = new Page<>(1, 20);
        emptyPage.setTotal(0);
        when(convMapper.listUserConversations(any(IPage.class), eq(CREATOR))).thenReturn(emptyPage);

        ListConversationsReq req = new ListConversationsReq(CREATOR, 0, 0);
        convService.listConversations(req);

        ArgumentCaptor<IPage<Conversation>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(convMapper).listUserConversations(captor.capture(), eq(CREATOR));
        IPage<Conversation> usedPage = captor.getValue();
        assertEquals(1L, usedPage.getCurrent());
        assertEquals(20L, usedPage.getSize());
    }

    @Test
    void getMembers_empty_returnsEmptyList() {
        // 读权限校验：会话存在 + 调用者是成员（requireMember 未 stubbed → mock 返回 null 但不抛）
        when(convMapper.selectById(CONV_ID))
                .thenReturn(mockConv(CONV_ID, ConvType.GROUP, CREATOR, 1));
        Page<ConversationMember> emptyPage = new Page<>(1, 20);
        emptyPage.setTotal(0);
        when(memberMapper.selectPage(any(IPage.class), any())).thenReturn(emptyPage);

        GetMembersReq req = new GetMembersReq(CONV_ID, CREATOR, 1, 20);
        GetMembersResp resp = convService.getMembers(req);

        assertNotNull(resp.getMembers());
        assertTrue(resp.getMembers().isEmpty());
        assertEquals(0L, resp.getTotal());
        // 空成员不查 readSeqs / userRpcService
        verify(readSeqMapper, never()).selectList(any());
        verify(userRpcService, never()).batchGetUserInfo(any());
    }

    @Test
    void getMembers_normal_fillLastReadSeqAndUserInfo() {
        when(convMapper.selectById(CONV_ID))
                .thenReturn(mockConv(CONV_ID, ConvType.GROUP, CREATOR, 2));
        ConversationMember m1 = mockMember(CONV_ID, CREATOR, MemberRole.OWNER);
        ConversationMember m2 = mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER);
        Page<ConversationMember> page = new Page<>(1, 20);
        page.setRecords(List.of(m1, m2));
        page.setTotal(2);
        when(memberMapper.selectPage(any(IPage.class), any())).thenReturn(page);

        // mock readSeqs：CREATOR 已读到 100，MEMBER_A 无记录（默认 0）
        ConvReadSeq rs1 = new ConvReadSeq();
        rs1.setConvId(CONV_ID);
        rs1.setUserId(CREATOR);
        rs1.setLastReadSeq(100L);
        when(readSeqMapper.selectList(any())).thenReturn(List.of(rs1));

        // mock 用户信息
        UserInfo u1 = new UserInfo();
        u1.setId(CREATOR);
        u1.setUsername("creator_name");
        u1.setAvatar("avatar1.jpg");
        UserInfo u2 = new UserInfo();
        u2.setId(MEMBER_A);
        u2.setUsername("member_a_name");
        u2.setAvatar("avatar2.jpg");
        when(userRpcService.batchGetUserInfo(any())).thenReturn(new BatchGetUserInfoResp(List.of(u1, u2)));

        GetMembersReq req = new GetMembersReq(CONV_ID, CREATOR, 1, 20);
        GetMembersResp resp = convService.getMembers(req);

        assertEquals(2, resp.getMembers().size());
        assertEquals(2L, resp.getTotal());

        ConversationMemberDTO dto1 = resp.getMembers().get(0);
        assertEquals(CREATOR, dto1.getUserId());
        assertEquals("creator_name", dto1.getUsername());
        assertEquals("avatar1.jpg", dto1.getAvatar());
        assertEquals(MemberRole.OWNER, dto1.getRole());
        assertEquals(100L, dto1.getLastReadSeq());
        assertEquals(1, dto1.getMemberType()); // 'user' → 1

        ConversationMemberDTO dto2 = resp.getMembers().get(1);
        assertEquals(MEMBER_A, dto2.getUserId());
        assertEquals("member_a_name", dto2.getUsername());
        assertEquals(MemberRole.MEMBER, dto2.getRole());
        assertEquals(0L, dto2.getLastReadSeq(), "无 readSeq 记录默认 0");
    }

    @Test
    void getMembers_userRpcReturnsNull_fallbackEmptyUsername() {
        when(convMapper.selectById(CONV_ID))
                .thenReturn(mockConv(CONV_ID, ConvType.GROUP, CREATOR, 1));
        ConversationMember m1 = mockMember(CONV_ID, CREATOR, MemberRole.OWNER);
        Page<ConversationMember> page = new Page<>(1, 20);
        page.setRecords(List.of(m1));
        page.setTotal(1);
        when(memberMapper.selectPage(any(IPage.class), any())).thenReturn(page);
        when(readSeqMapper.selectList(any())).thenReturn(List.of());
        // userRpcService 返回 null（容错验证）
        when(userRpcService.batchGetUserInfo(any())).thenReturn(null);

        GetMembersReq req = new GetMembersReq(CONV_ID, CREATOR, 1, 20);
        GetMembersResp resp = convService.getMembers(req);

        assertEquals(1, resp.getMembers().size());
        assertEquals("", resp.getMembers().get(0).getUsername());
        assertEquals("", resp.getMembers().get(0).getAvatar());
    }

    @Test
    void isMember_exist_returnsTrue() {
        when(permissionChecker.getMember(CONV_ID, CREATOR))
                .thenReturn(mockMember(CONV_ID, CREATOR, MemberRole.OWNER));
        assertTrue(convService.isMember(CONV_ID, CREATOR));
    }

    @Test
    void isMember_notExist_returnsFalse() {
        when(permissionChecker.getMember(CONV_ID, CREATOR)).thenReturn(null);
        assertFalse(convService.isMember(CONV_ID, CREATOR));
    }

    // ==================== Phase 1.4 消息链路 + 事务后置 ====================

    @Test
    void preCheckSend_memberNotMuted_returnsMemberInfo() {
        ConversationMember member = mockMember(CONV_ID, CREATOR, MemberRole.MEMBER);
        member.setIsMuted(false);
        member.setMuteUntil(0L);
        when(permissionChecker.getMember(CONV_ID, CREATOR)).thenReturn(member);
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        conv.setIsMutedAll(false);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        // mock 成员列表查询
        when(memberMapper.selectList(any())).thenReturn(List.of(
                mockMember(CONV_ID, CREATOR, MemberRole.MEMBER),
                mockMember(CONV_ID, MEMBER_A, MemberRole.MEMBER)));

        PreCheckSendReq req = new PreCheckSendReq(CONV_ID, CREATOR);
        PreCheckSendResp resp = convService.preCheckSend(req);

        assertTrue(resp.isMember(), "成员身份应被识别");
        assertFalse(resp.isMuted(), "未被禁言");
        assertFalse(resp.isMutedAll(), "全员未禁言");
        assertEquals(0L, resp.getMuteUntil());
        assertEquals(ConvType.GROUP, resp.getConvType());
        assertEquals(2, resp.getMemberIds().size(), "返回全量 memberIds 供 message-service 扇出");
    }

    @Test
    void preCheckSend_nonMember_stillReturnsNoIntercept() {
        // 非成员：isMember=false，但仍返回（不拦截，决策 14）
        when(permissionChecker.getMember(CONV_ID, CREATOR)).thenReturn(null);
        Conversation conv = mockConv(CONV_ID, ConvType.SINGLE, 0L, 2);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        when(memberMapper.selectList(any())).thenReturn(List.of());

        PreCheckSendReq req = new PreCheckSendReq(CONV_ID, CREATOR);
        PreCheckSendResp resp = convService.preCheckSend(req);

        assertFalse(resp.isMember(), "非成员 isMember=false");
        assertFalse(resp.isMuted(), "非成员默认未禁言");
        assertEquals(0L, resp.getMuteUntil());
        assertTrue(resp.getMemberIds().isEmpty());
    }

    @Test
    void preCheckSend_memberMuted_returnsMuteInfo() {
        ConversationMember member = mockMember(CONV_ID, CREATOR, MemberRole.MEMBER);
        member.setIsMuted(true);
        member.setMuteUntil(99999L);
        when(permissionChecker.getMember(CONV_ID, CREATOR)).thenReturn(member);
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        conv.setIsMutedAll(true);  // 全员禁言
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        when(memberMapper.selectList(any())).thenReturn(List.of());

        PreCheckSendReq req = new PreCheckSendReq(CONV_ID, CREATOR);
        PreCheckSendResp resp = convService.preCheckSend(req);

        assertTrue(resp.isMember());
        assertTrue(resp.isMuted(), "个人被禁言");
        assertTrue(resp.isMutedAll(), "全员禁言");
        assertEquals(99999L, resp.getMuteUntil());
    }

    @Test
    void preCheckSend_convNotFound_nonCriticalSwallow() {
        // conv 查不到时不抛异常，只 log（非关键信息，spec 第 12.2 节）
        when(permissionChecker.getMember(CONV_ID, CREATOR))
                .thenReturn(mockMember(CONV_ID, CREATOR, MemberRole.MEMBER));
        when(convMapper.selectById(CONV_ID)).thenReturn(null);
        when(memberMapper.selectList(any())).thenReturn(List.of());

        PreCheckSendReq req = new PreCheckSendReq(CONV_ID, CREATOR);
        PreCheckSendResp resp = convService.preCheckSend(req);

        assertTrue(resp.isMember());
        assertEquals(0, resp.getConvType(), "conv 不存在时 convType=0");
        assertFalse(resp.isMutedAll());
    }

    @Test
    void updateLastMessage_convNotExist_logAndReturn() {
        when(convMapper.selectById(CONV_ID)).thenReturn(null);

        // 不抛异常，仅 log
        UpdateLastMessageReq req = new UpdateLastMessageReq(CONV_ID, 9001L, 100L, "hi");
        convService.updateLastMessage(req);

        // 不存在时不调用幂等更新
        verify(convMapper, never()).updateLastMessageSeq(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void updateLastMessage_normal_callIdempotentUpdate() {
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        conv.setMaxSeq(50L);
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        when(convMapper.updateLastMessageSeq(CONV_ID, 9001L, 100L, "hello"))
                .thenReturn(1);

        UpdateLastMessageReq req = new UpdateLastMessageReq(CONV_ID, 9001L, 100L, "hello");
        convService.updateLastMessage(req);

        // 验证幂等更新被调用
        verify(convMapper).updateLastMessageSeq(CONV_ID, 9001L, 100L, "hello");
    }

    @Test
    void updateLastMessage_seqLag_skippedByWhereClause() {
        // seq 落后时，WHERE max_seq < #{seq} 不命中，affectedRows=0，跳过
        Conversation conv = mockConv(CONV_ID, ConvType.GROUP, CREATOR, 3);
        conv.setMaxSeq(200L);  // 已有 maxSeq=200
        when(convMapper.selectById(CONV_ID)).thenReturn(conv);
        when(convMapper.updateLastMessageSeq(CONV_ID, 9001L, 100L, "old"))
                .thenReturn(0);  // 0 表示跳过

        UpdateLastMessageReq req = new UpdateLastMessageReq(CONV_ID, 9001L, 100L, "old");
        convService.updateLastMessage(req);

        // 幂等更新仍被调用，但 DB 层 WHERE 不命中
        verify(convMapper).updateLastMessageSeq(CONV_ID, 9001L, 100L, "old");
    }

    @Test
    void markRead_callsUpsertAndPublishSpringEvent() {
        // 读权限校验：会话存在 + 调用者是成员（requireMember 通过）
        when(convMapper.selectById(CONV_ID))
                .thenReturn(mockConv(CONV_ID, ConvType.GROUP, CREATOR, 1));
        when(permissionChecker.getMember(CONV_ID, CREATOR))
                .thenReturn(mockMember(CONV_ID, CREATOR, MemberRole.MEMBER));
        when(snowflake.nextId()).thenReturn(7777L);

        MarkReadReq req = new MarkReadReq(CREATOR, CONV_ID, 100L);
        convService.markRead(req);

        // 验证 UPSERT 已读位置
        verify(readSeqMapper).upsertReadSeq(7777L, CONV_ID, CREATOR, 100L);

        // 验证事务内只 publish Spring 内部事件
        ArgumentCaptor<Object> evtCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(evtCaptor.capture());
        Object published = evtCaptor.getValue();
        assertInstanceOf(MarkReadCompletedEvent.class, published);
        MarkReadCompletedEvent mrce = (MarkReadCompletedEvent) published;
        assertEquals(CREATOR, mrce.getUserId());
        assertEquals(CONV_ID, mrce.getConvId());
        assertEquals(100L, mrce.getLastReadSeq());

        // 外部系统（Redis DEL + Kafka 发事件）不在事务内执行，由 AFTER_COMMIT listener 触发
        verify(unreadCache, never()).clearUnreadCount(anyLong(), anyLong());
        verify(eventPublisher, never()).publishReadUpdated(anyLong(), anyLong(), anyLong());
    }

    @Test
    void getSettings_noRecord_returnsDefaults() {
        when(settingsMapper.selectOne(any())).thenReturn(null);
        when(permissionChecker.getMember(CONV_ID, CREATOR))
                .thenReturn(mockMember(CONV_ID, CREATOR, MemberRole.MEMBER));

        GetSettingsReq req = new GetSettingsReq(CREATOR, CONV_ID);
        GetSettingsResp resp = convService.getSettings(req);

        assertFalse(resp.isMuted(), "无记录默认 false");
        assertFalse(resp.isPinned(), "无记录默认 false");
        assertEquals("", resp.getNickname(), "无 alias 默认空字符串");
    }

    @Test
    void getSettings_hasRecord_returnsValues() {
        ConvSettings settings = new ConvSettings();
        settings.setIsMuted(true);
        settings.setIsPinned(true);
        when(settingsMapper.selectOne(any())).thenReturn(settings);
        ConversationMember member = mockMember(CONV_ID, CREATOR, MemberRole.MEMBER);
        member.setAlias("我的备注");
        when(permissionChecker.getMember(CONV_ID, CREATOR)).thenReturn(member);

        GetSettingsReq req = new GetSettingsReq(CREATOR, CONV_ID);
        GetSettingsResp resp = convService.getSettings(req);

        assertTrue(resp.isMuted());
        assertTrue(resp.isPinned());
        assertEquals("我的备注", resp.getNickname(), "nickname 来自 conv_members.alias");
    }

    @Test
    void updateSettings_callsUpsertWithCoalesceSemantics() {
        when(snowflake.nextId()).thenReturn(8888L);
        // isMuted=true, isPinned=null（不更新），nickname=null
        UpdateSettingsReq req = new UpdateSettingsReq(CREATOR, CONV_ID, true, null, null);

        convService.updateSettings(req);

        // UPSERT 调用：isMuted=true, isPinned=null（COALESCE 处理 null=不更新）
        verify(settingsMapper).upsertSettings(8888L, CONV_ID, CREATOR, true, null);
        // nickname 为 null/空时不更新 conv_members.alias
        verify(permissionChecker, never()).getMember(anyLong(), anyLong());
    }

    @Test
    void updateSettings_withNickname_updatesAlias() {
        when(snowflake.nextId()).thenReturn(8889L);
        ConversationMember member = mockMember(CONV_ID, CREATOR, MemberRole.MEMBER);
        when(permissionChecker.getMember(CONV_ID, CREATOR)).thenReturn(member);

        UpdateSettingsReq req = new UpdateSettingsReq(CREATOR, CONV_ID, false, true, "新备注");
        convService.updateSettings(req);

        // 验证 UPSERT 调用
        verify(settingsMapper).upsertSettings(8889L, CONV_ID, CREATOR, false, true);
        // 验证 conv_members.alias 被更新
        assertEquals("新备注", member.getAlias());
        verify(memberMapper).updateById(member);
    }
}
