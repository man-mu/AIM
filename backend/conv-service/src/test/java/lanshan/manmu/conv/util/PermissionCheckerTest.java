package lanshan.manmu.conv.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.conv.mapper.ConversationMemberMapper;
import lanshan.manmu.conv.model.entity.ConversationMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * PermissionChecker 纯 Mockito 单测（spec 第 18.1 节）。
 * <p>覆盖 requireRole/requireOwner/requireAdmin/verifyTargetNotHigher/getMember。
 */
class PermissionCheckerTest {

    private ConversationMemberMapper memberMapper;
    private PermissionChecker permissionChecker;

    private static final long CONV_ID  = 1001L;
    private static final long OPERATOR = 2001L;
    private static final long TARGET   = 2002L;

    @BeforeEach
    void setUp() {
        memberMapper = Mockito.mock(ConversationMemberMapper.class);
        permissionChecker = new PermissionChecker(memberMapper);
    }

    private ConversationMember mockMember(long userId, int role) {
        ConversationMember m = new ConversationMember();
        m.setConvId(CONV_ID);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    // ==================== requireRole ====================

    @Test
    void requireRole_nonMember_throwsConvNotMember() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.requireRole(CONV_ID, OPERATOR, MemberRole.MEMBER));
        assertEquals(ErrorCode.CONV_NOT_MEMBER.getCode(), ex.getCode());
    }

    @Test
    void requireRole_insufficientRole_throwsPermissionDenied() {
        // 操作者是 MEMBER(3)，要求 ADMIN(2) 或更小
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(OPERATOR, MemberRole.MEMBER));

        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.requireRole(CONV_ID, OPERATOR, MemberRole.ADMIN));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void requireRole_sufficientRole_returnsMember() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(OPERATOR, MemberRole.OWNER));

        ConversationMember result = permissionChecker.requireRole(CONV_ID, OPERATOR, MemberRole.ADMIN);
        assertNotNull(result);
        assertEquals(MemberRole.OWNER, result.getRole());
    }

    // ==================== requireOwner / requireAdmin ====================

    @Test
    void requireOwner_owner_pass() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(OPERATOR, MemberRole.OWNER));
        ConversationMember result = permissionChecker.requireOwner(CONV_ID, OPERATOR);
        assertEquals(MemberRole.OWNER, result.getRole());
    }

    @Test
    void requireOwner_admin_throwsPermissionDenied() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(OPERATOR, MemberRole.ADMIN));
        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.requireOwner(CONV_ID, OPERATOR));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void requireAdmin_owner_pass() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(OPERATOR, MemberRole.OWNER));
        ConversationMember result = permissionChecker.requireAdmin(CONV_ID, OPERATOR);
        assertEquals(MemberRole.OWNER, result.getRole());
    }

    @Test
    void requireAdmin_admin_pass() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(OPERATOR, MemberRole.ADMIN));
        ConversationMember result = permissionChecker.requireAdmin(CONV_ID, OPERATOR);
        assertEquals(MemberRole.ADMIN, result.getRole());
    }

    @Test
    void requireAdmin_member_throwsPermissionDenied() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(OPERATOR, MemberRole.MEMBER));
        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.requireAdmin(CONV_ID, OPERATOR));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    // ==================== verifyTargetNotHigher ====================

    @Test
    void verifyTargetNotHigher_targetNotExist_throwsMemberNotFound() {
        // 第一次 selectOne 返回 null（target），但 spec 实现里 target==null 立即抛
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.verifyTargetNotHigher(CONV_ID, TARGET, OPERATOR));
        assertEquals(ErrorCode.CONV_MEMBER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void verifyTargetNotHigher_operatorNotExist_throwsConvNotMember() {
        // 第一次（target）非空，第二次（operator）null
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(TARGET, MemberRole.MEMBER))
                .thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.verifyTargetNotHigher(CONV_ID, TARGET, OPERATOR));
        assertEquals(ErrorCode.CONV_NOT_MEMBER.getCode(), ex.getCode());
    }

    @Test
    void verifyTargetNotHigher_targetSameRole_throwsPermissionDenied() {
        // target=ADMIN(2), operator=ADMIN(2)，target.role <= operator.role → 拒绝
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(TARGET, MemberRole.ADMIN))
                .thenReturn(mockMember(OPERATOR, MemberRole.ADMIN));

        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.verifyTargetNotHigher(CONV_ID, TARGET, OPERATOR));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void verifyTargetNotHigher_targetHigherRole_throwsPermissionDenied() {
        // target=OWNER(1), operator=ADMIN(2)，target.role <= operator.role → 拒绝（不能对上级操作）
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(TARGET, MemberRole.OWNER))
                .thenReturn(mockMember(OPERATOR, MemberRole.ADMIN));

        BizException ex = assertThrows(BizException.class,
                () -> permissionChecker.verifyTargetNotHigher(CONV_ID, TARGET, OPERATOR));
        assertEquals(ErrorCode.CONV_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void verifyTargetNotHigher_targetLowerRole_pass() {
        // target=MEMBER(3), operator=ADMIN(2)，target.role > operator.role → 通过
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockMember(TARGET, MemberRole.MEMBER))
                .thenReturn(mockMember(OPERATOR, MemberRole.ADMIN));

        assertDoesNotThrow(() ->
                permissionChecker.verifyTargetNotHigher(CONV_ID, TARGET, OPERATOR));
    }

    // ==================== getMember ====================

    @Test
    void getMember_notExist_returnsNull() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(permissionChecker.getMember(CONV_ID, OPERATOR));
    }

    @Test
    void getMember_exist_returnsMember() {
        ConversationMember member = mockMember(OPERATOR, MemberRole.ADMIN);
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);
        ConversationMember result = permissionChecker.getMember(CONV_ID, OPERATOR);
        assertNotNull(result);
        assertEquals(OPERATOR, result.getUserId());
    }
}
