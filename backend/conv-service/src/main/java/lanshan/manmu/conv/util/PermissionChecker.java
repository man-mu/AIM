package lanshan.manmu.conv.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.conv.mapper.ConversationMemberMapper;
import lanshan.manmu.conv.model.entity.ConversationMember;
import org.springframework.stereotype.Component;

@Component
public class PermissionChecker {

    private final ConversationMemberMapper memberMapper;

    public PermissionChecker(ConversationMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /** 要求操作者角色 <= minRole。返回操作者 member 记录供复用 */
    public ConversationMember requireRole(long convId, long operatorId, int minRole) {
        ConversationMember member = getMember(convId, operatorId);
        if (member == null) {
            throw new BizException(ErrorCode.CONV_NOT_MEMBER, "operator not in conv " + convId);
        }
        if (member.getRole() > minRole) {
            throw new BizException(ErrorCode.CONV_PERMISSION_DENIED, "require role<=" + minRole);
        }
        return member;
    }

    /** 要求操作者是 OWNER */
    public ConversationMember requireOwner(long convId, long operatorId) {
        return requireRole(convId, operatorId, MemberRole.OWNER);
    }

    /** 要求操作者是 ADMIN 或 OWNER */
    public ConversationMember requireAdmin(long convId, long operatorId) {
        return requireRole(convId, operatorId, MemberRole.ADMIN);
    }

    /**
     * 要求操作者是会话成员（任意角色：OWNER/ADMIN/MEMBER 均通过）。
     * <p>复用 {@link #requireRole}，传入 {@link MemberRole#MEMBER}（角色值最大=3），
     * 故任意有效成员 role 都 <= MEMBER，不会触发权限拒绝；仅当非成员（member==null）时抛
     * {@link ErrorCode#CONV_NOT_MEMBER}(30004)。
     */
    public ConversationMember requireMember(long convId, long userId) {
        return requireRole(convId, userId, MemberRole.MEMBER);
    }

    /** 验证目标角色 > 操作者角色（不能对同级/上级操作） */
    public void verifyTargetNotHigher(long convId, long targetId, long operatorId) {
        ConversationMember target = getMember(convId, targetId);
        if (target == null) {
            throw new BizException(ErrorCode.CONV_MEMBER_NOT_FOUND, "target " + targetId);
        }
        ConversationMember operator = getMember(convId, operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.CONV_NOT_MEMBER, "operator " + operatorId);
        }
        if (target.getRole() <= operator.getRole()) {
            throw new BizException(ErrorCode.CONV_PERMISSION_DENIED, "cannot operate on same/higher role");
        }
    }

    public ConversationMember getMember(long convId, long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConvId, convId)
                .eq(ConversationMember::getUserId, userId));
    }
}
