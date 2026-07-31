package lanshan.manmu.conv.event;

import lanshan.manmu.conv.util.UnreadCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * conv-service 事务后置监听器：在 DB 事务 AFTER_COMMIT 阶段执行外部系统操作
 * （Redis 未读清零 / Kafka 发事件）。
 * <p>关键约束：DB 事务已提交，外部操作失败既不能再回滚 DB，也不能让异常穿透回
 * markRead 等调用线程——否则客户端会收到 500，且后续外部操作会被跳过。
 * 故每个外部操作独立 try/catch + log.error：互不影响、不向上传播。
 * publishAfterCommit（在 ConvServiceImpl 内注册 Spring 内部事件）的逻辑保持不变。
 */
@Component
@Slf4j
public class ConvEventListener {

    private final UnreadCacheService unreadCache;
    private final ConvEventPublisher eventPublisher;

    public ConvEventListener(UnreadCacheService unreadCache, ConvEventPublisher eventPublisher) {
        this.unreadCache = unreadCache;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarkReadCompleted(MarkReadCompletedEvent evt) {
        // DB 事务已提交，此时执行外部系统操作；每个操作独立 try/catch，避免任一失败穿透到
        // markRead 调用线程（DB 已提交却返回 500）或跳过后续操作。
        long userId = evt.getUserId();
        long convId = evt.getConvId();
        long lastReadSeq = evt.getLastReadSeq();
        try {
            unreadCache.clearUnreadCount(userId, convId);
        } catch (Exception e) {
            log.error("clear unread count failed after commit userId={} convId={}", userId, convId, e);
        }
        try {
            eventPublisher.publishReadUpdated(convId, userId, lastReadSeq);
        } catch (Exception e) {
            log.error("publish read updated event failed after commit convId={} userId={} lastReadSeq={}",
                    convId, userId, lastReadSeq, e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembersJoined(MembersJoinedEvent evt) {
        try {
            eventPublisher.publishMemberJoined(evt.getConvId(), evt.getUserIds(), evt.getJoinedBy());
        } catch (Exception e) {
            log.error("publish member joined event failed after commit convId={} joinedBy={}",
                    evt.getConvId(), evt.getJoinedBy(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembersLeft(MembersLeftEvent evt) {
        try {
            eventPublisher.publishMemberLeft(evt.getConvId(), evt.getUserIds(), evt.getRemovedBy());
        } catch (Exception e) {
            log.error("publish member left event failed after commit convId={} removedBy={}",
                    evt.getConvId(), evt.getRemovedBy(), e);
        }
    }
}