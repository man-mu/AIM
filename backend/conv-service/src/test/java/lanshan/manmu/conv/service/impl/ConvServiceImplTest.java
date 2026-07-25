package lanshan.manmu.conv.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.common.rpc.dto.conv.*;
import lanshan.manmu.common.util.SnowflakeIdWorker;
import lanshan.manmu.conv.event.ConvEventPublisher;
import lanshan.manmu.conv.event.MembersJoinedEvent;
import lanshan.manmu.conv.event.MembersLeftEvent;
import lanshan.manmu.conv.mapper.ConvReadSeqMapper;
import lanshan.manmu.conv.mapper.ConvSettingsMapper;
import lanshan.manmu.conv.mapper.ConversationMapper;
import lanshan.manmu.conv.mapper.ConversationMemberMapper;
import lanshan.manmu.conv.model.entity.Conversation;
import lanshan.manmu.conv.model.entity.ConversationMember;
import lanshan.manmu.conv.util.ConvConstants;
import lanshan.manmu.conv.util.PermissionChecker;
import lanshan.manmu.conv.util.UnreadCacheService;
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
}
