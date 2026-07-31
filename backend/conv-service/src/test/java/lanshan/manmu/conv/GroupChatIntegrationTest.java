package lanshan.manmu.conv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.rpc.dto.conv.AddMembersReq;
import lanshan.manmu.common.rpc.dto.conv.AddMembersResp;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationReq;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationResp;
import lanshan.manmu.common.rpc.dto.conv.RemoveMembersReq;
import lanshan.manmu.common.rpc.dto.conv.TransferOwnerReq;
import lanshan.manmu.conv.mapper.ConversationMemberMapper;
import lanshan.manmu.conv.mapper.ConversationMapper;
import lanshan.manmu.conv.model.entity.Conversation;
import lanshan.manmu.conv.model.entity.ConversationMember;
import lanshan.manmu.conv.service.ConvService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * spec 18.2 场景 2：群聊端到端。
 * <p>A 创建群→拉 B、C→A 转让给 B→A 变 MEMBER, B 变 OWNER→A 自退
 */
class GroupChatIntegrationTest extends ConvIntegrationTestBase {

    @Autowired ConvService convService;
    @Autowired ConversationMapper convMapper;
    @Autowired ConversationMemberMapper memberMapper;

    private static final long USER_A = 2001L;
    private static final long USER_B = 2002L;
    private static final long USER_C = 2003L;

    @Test
    void test_groupChatEndToEnd() {
        // 1. A 创建群（含 B、C）
        CreateConversationResp resp = convService.createConversation(
                new CreateConversationReq(ConvType.GROUP, USER_A, null, "test-group", "", List.of(USER_B, USER_C)));
        long convId = resp.getConversationId();
        assertThat(convId).isGreaterThan(0);

        // 验证会话信息
        Conversation conv = convMapper.selectById(convId);
        assertThat(conv.getType()).isEqualTo(ConvType.GROUP);
        assertThat(conv.getName()).isEqualTo("test-group");
        assertThat(conv.getOwnerId()).isEqualTo(USER_A);
        assertThat(conv.getMemberCount()).isEqualTo(3);

        // 验证 A=OWNER，B/C=MEMBER
        assertThat(findMember(convId, USER_A).getRole()).isEqualTo(MemberRole.OWNER);
        assertThat(findMember(convId, USER_B).getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(findMember(convId, USER_C).getRole()).isEqualTo(MemberRole.MEMBER);

        // 2. A 转让群主给 B
        convService.transferOwner(new TransferOwnerReq(convId, USER_A, USER_B));

        // 验证 A 变 MEMBER，B 变 OWNER，conv.ownerId=B
        Conversation transferred = convMapper.selectById(convId);
        assertThat(transferred.getOwnerId()).isEqualTo(USER_B);
        assertThat(findMember(convId, USER_A).getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(findMember(convId, USER_B).getRole()).isEqualTo(MemberRole.OWNER);

        // 3. A 自退（removeMembers 自己）
        convService.removeMembers(new RemoveMembersReq(convId, USER_A, List.of(USER_A)));

        // 验证 A 已不在群，memberCount=2
        assertThat(findMember(convId, USER_A)).isNull();
        Conversation afterLeave = convMapper.selectById(convId);
        assertThat(afterLeave.getMemberCount()).isEqualTo(2);
    }

    private ConversationMember findMember(long convId, long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConvId, convId)
                .eq(ConversationMember::getUserId, userId));
    }
}
