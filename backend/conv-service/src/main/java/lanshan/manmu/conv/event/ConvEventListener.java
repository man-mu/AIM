package lanshan.manmu.conv.event;

import lanshan.manmu.conv.util.UnreadCacheService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ConvEventListener {

    private final UnreadCacheService unreadCache;
    private final ConvEventPublisher eventPublisher;

    public ConvEventListener(UnreadCacheService unreadCache, ConvEventPublisher eventPublisher) {
        this.unreadCache = unreadCache;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarkReadCompleted(MarkReadCompletedEvent evt) {
        // DB 事务已提交，此时执行外部系统操作；即使失败也不影响 DB 状态
        unreadCache.clearUnreadCount(evt.getUserId(), evt.getConvId());
        eventPublisher.publishReadUpdated(evt.getConvId(), evt.getUserId(), evt.getLastReadSeq());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembersJoined(MembersJoinedEvent evt) {
        eventPublisher.publishMemberJoined(evt.getConvId(), evt.getUserIds(), evt.getJoinedBy());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembersLeft(MembersLeftEvent evt) {
        eventPublisher.publishMemberLeft(evt.getConvId(), evt.getUserIds(), evt.getRemovedBy());
    }
}
