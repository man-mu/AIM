package lanshan.manmu.conv.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UnreadCacheService {

    private final StringRedisTemplate redis;
    private static final String KEY_PREFIX = "aim:unread:";

    public UnreadCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 清零某用户在某会话的未读数 */
    public void clearUnreadCount(long userId, long convId) {
        String key = KEY_PREFIX + userId + ":" + convId;
        redis.delete(key);
        log.debug("clear unread count userId={} convId={}", userId, convId);
    }

    /** 读取某用户在某会话的未读数（fallback 0） */
    public long getUnreadCount(long userId, long convId) {
        String val = redis.opsForValue().get(KEY_PREFIX + userId + ":" + convId);
        if (val == null) return 0L;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 批量读取某用户所有会话的未读数（用于 listConversations） */
    public Map<Long, Long> batchGetUnread(long userId, Collection<Long> convIds) {
        List<String> keys = convIds.stream().map(id -> KEY_PREFIX + userId + ":" + id).toList();
        List<String> vals = redis.opsForValue().multiGet(keys);
        Map<Long, Long> result = new HashMap<>();
        int i = 0;
        for (Long convId : convIds) {
            String v = (vals == null || i >= vals.size()) ? null : vals.get(i);
            result.put(convId, v == null ? 0L : Long.parseLong(v));
            i++;
        }
        return result;
    }
}
