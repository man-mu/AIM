package lanshan.manmu.conv.service.impl;

import java.util.ArrayList;
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
import lanshan.manmu.conv.service.ConvService;
import lanshan.manmu.conv.util.ConvConstants;
import lanshan.manmu.conv.util.PermissionChecker;
import lanshan.manmu.conv.util.UnreadCacheService;
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

    // ==================== Phase 1.3/1.4 待实现 ====================

    @Override
    public ConversationDTO getConversation(long conversationId, long userId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public ListConversationsResp listConversations(ListConversationsReq req) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public GetMembersResp getMembers(GetMembersReq req) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public boolean isMember(long conversationId, long userId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public PreCheckSendResp preCheckSend(PreCheckSendReq req) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void markRead(MarkReadReq req) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void updateLastMessage(UpdateLastMessageReq req) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public GetSettingsResp getSettings(GetSettingsReq req) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void updateSettings(UpdateSettingsReq req) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    // ==================== 私有工具方法 ====================

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
        return dto;
    }
}
