package lanshan.manmu.conv.controller;

import java.time.Instant;
import java.util.List;
import lanshan.manmu.common.result.Result;
import lanshan.manmu.common.rpc.dto.conv.AddMembersReq;
import lanshan.manmu.common.rpc.dto.conv.AddMembersResp;
import lanshan.manmu.common.rpc.dto.conv.ConversationDTO;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationReq;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationResp;
import lanshan.manmu.common.rpc.dto.conv.GetMembersReq;
import lanshan.manmu.common.rpc.dto.conv.GetMembersResp;
import lanshan.manmu.common.rpc.dto.conv.GetSettingsReq;
import lanshan.manmu.common.rpc.dto.conv.GetSettingsResp;
import lanshan.manmu.common.rpc.dto.conv.ListConversationsReq;
import lanshan.manmu.common.rpc.dto.conv.ListConversationsResp;
import lanshan.manmu.common.rpc.dto.conv.MarkReadReq;
import lanshan.manmu.common.rpc.dto.conv.MuteMemberReq;
import lanshan.manmu.common.rpc.dto.conv.RemoveMembersReq;
import lanshan.manmu.common.rpc.dto.conv.TransferOwnerReq;
import lanshan.manmu.common.rpc.dto.conv.UpdateSettingsReq;
import lanshan.manmu.conv.service.ConvService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话 Controller（spec controller-spec.md §6）。
 * <p>路径对齐 api-v1.md §5：{@code /api/v1/convs/**}。
 * <p>所有接口都需鉴权，从网关注入的 {@code X-User-Id} header 取当前用户。
 * <p>API 文档字段 ↔ DTO 字段映射：
 * <ul>
 *   <li>mute.durationSeconds → MuteMemberReq.muteUntil（epoch 秒）</li>
 *   <li>transfer.newOwnerId → TransferOwnerReq.toUserId</li>
 *   <li>markRead.seq → MarkReadReq.lastReadSeq</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/convs")
public class ConvController {

    private final ConvService convService;

    public ConvController(ConvService convService) {
        this.convService = convService;
    }

    @PostMapping
    public Result<CreateConversationResp> create(@RequestHeader("X-User-Id") long userId,
                                                  @RequestBody CreateConversationReq req) {
        req.setCreatorId(userId);
        return Result.ok(convService.createConversation(req));
    }

    @GetMapping("/{conversationId}")
    public Result<ConversationDTO> get(@RequestHeader("X-User-Id") long userId,
                                        @PathVariable("conversationId") long conversationId) {
        return Result.ok(convService.getConversation(conversationId, userId));
    }

    @GetMapping
    public Result<ListConversationsResp> list(@RequestHeader("X-User-Id") long userId,
                                                @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        ListConversationsReq req = new ListConversationsReq(userId, pageNum, pageSize);
        return Result.ok(convService.listConversations(req));
    }

    @PostMapping("/{conversationId}/members/invite")
    public Result<AddMembersResp> invite(@RequestHeader("X-User-Id") long userId,
                                          @PathVariable("conversationId") long conversationId,
                                          @RequestBody InviteBody body) {
        AddMembersReq req = new AddMembersReq(conversationId, userId, body.getUserIds());
        return Result.ok(convService.addMembers(req));
    }

    @PostMapping("/{conversationId}/members/kick")
    public Result<Void> kick(@RequestHeader("X-User-Id") long userId,
                              @PathVariable("conversationId") long conversationId,
                              @RequestBody KickBody body) {
        RemoveMembersReq req = new RemoveMembersReq(conversationId, userId, body.getUserIds());
        convService.removeMembers(req);
        return Result.ok();
    }

    @GetMapping("/{conversationId}/members")
    public Result<GetMembersResp> members(@RequestHeader("X-User-Id") long userId,
                                           @PathVariable("conversationId") long conversationId,
                                           @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                           @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        GetMembersReq req = new GetMembersReq(conversationId, userId, pageNum, pageSize);
        return Result.ok(convService.getMembers(req));
    }

    @PutMapping("/{conversationId}/members/{userId}/mute")
    public Result<Void> mute(@RequestHeader("X-User-Id") long operatorId,
                              @PathVariable("conversationId") long conversationId,
                              @PathVariable("userId") long targetUserId,
                              @RequestBody MuteBody body) {
        // durationSeconds=0 表示永久禁言（muteUntil=0），否则按 epoch 秒计算
        long muteUntil = body.getDurationSeconds() == 0
                ? 0L
                : Instant.now().getEpochSecond() + body.getDurationSeconds();
        MuteMemberReq req = new MuteMemberReq(conversationId, operatorId, targetUserId, muteUntil);
        convService.muteMember(req);
        return Result.ok();
    }

    @DeleteMapping("/{conversationId}/members/{userId}/mute")
    public Result<Void> unmute(@RequestHeader("X-User-Id") long operatorId,
                                @PathVariable("conversationId") long conversationId,
                                @PathVariable("userId") long targetUserId) {
        // 解除禁言：独立写 is_muted=false / mute_until=0，与永久禁言（PUT 写 is_muted=true / mute_until=0）区分
        MuteMemberReq req = new MuteMemberReq(conversationId, operatorId, targetUserId, 0L);
        convService.unmuteMember(req);
        return Result.ok();
    }

    @PostMapping("/{conversationId}/transfer")
    public Result<Void> transfer(@RequestHeader("X-User-Id") long userId,
                                  @PathVariable("conversationId") long conversationId,
                                  @RequestBody TransferBody body) {
        TransferOwnerReq req = new TransferOwnerReq(conversationId, userId, body.getNewOwnerId());
        convService.transferOwner(req);
        return Result.ok();
    }

    @PutMapping("/{conversationId}/announcement")
    public Result<Void> updateAnnouncement(@RequestHeader("X-User-Id") long userId,
                                            @PathVariable("conversationId") long conversationId,
                                            @RequestBody AnnouncementBody body) {
        convService.updateAnnouncement(conversationId, userId, body.getContent());
        return Result.ok();
    }

    @DeleteMapping("/{conversationId}/announcement")
    public Result<Void> deleteAnnouncement(@RequestHeader("X-User-Id") long userId,
                                            @PathVariable("conversationId") long conversationId) {
        convService.updateAnnouncement(conversationId, userId, "");
        return Result.ok();
    }

    @GetMapping("/{conversationId}/settings")
    public Result<GetSettingsResp> getSettings(@RequestHeader("X-User-Id") long userId,
                                                @PathVariable("conversationId") long conversationId) {
        GetSettingsReq req = new GetSettingsReq(userId, conversationId);
        return Result.ok(convService.getSettings(req));
    }

    @PutMapping("/{conversationId}/settings")
    public Result<Void> updateSettings(@RequestHeader("X-User-Id") long userId,
                                        @PathVariable("conversationId") long conversationId,
                                        @RequestBody UpdateSettingsReq req) {
        req.setUserId(userId);
        req.setConversationId(conversationId);
        convService.updateSettings(req);
        return Result.ok();
    }

    @PutMapping("/{conversationId}/read")
    public Result<Void> markRead(@RequestHeader("X-User-Id") long userId,
                                  @PathVariable("conversationId") long conversationId,
                                  @RequestBody MarkReadBody body) {
        MarkReadReq req = new MarkReadReq(userId, conversationId, body.getSeq());
        convService.markRead(req);
        return Result.ok();
    }

    // ==================== 请求 Body 内部类（与 api-v1.md 字段名对齐） ====================

    /** invite 请求体：{ "userIds": [123, 456] } */
    public static class InviteBody {
        private List<Long> userIds;
        public List<Long> getUserIds() { return userIds; }
        public void setUserIds(List<Long> userIds) { this.userIds = userIds; }
    }

    /** kick 请求体：{ "userIds": [123] } */
    public static class KickBody {
        private List<Long> userIds;
        public List<Long> getUserIds() { return userIds; }
        public void setUserIds(List<Long> userIds) { this.userIds = userIds; }
    }

    /** mute 请求体：{ "durationSeconds": 3600 } */
    public static class MuteBody {
        private long durationSeconds;
        public long getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
    }

    /** transfer 请求体：{ "newOwnerId": 789 } */
    public static class TransferBody {
        private long newOwnerId;
        public long getNewOwnerId() { return newOwnerId; }
        public void setNewOwnerId(long newOwnerId) { this.newOwnerId = newOwnerId; }
    }

    /** announcement 请求体：{ "content": "..." } */
    public static class AnnouncementBody {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /** markRead 请求体：{ "seq": 10 } */
    public static class MarkReadBody {
        private long seq;
        public long getSeq() { return seq; }
        public void setSeq(long seq) { this.seq = seq; }
    }
}
