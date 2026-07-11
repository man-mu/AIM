package lanshan.manmu.common.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * SnowflakeIdWorker 单测。
 */
class SnowflakeIdWorkerTest {

    @Test
    void shouldGenerateIncreasingAndUniqueIds() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1);
        Set<Long> ids = new HashSet<>();
        long prev = 0;

        for (int i = 0; i < 10000; i++) {
            long id = worker.nextId();
            // 验证唯一性
            assertFalse(ids.contains(id), "ID 重复: " + id);
            ids.add(id);
            // 验证严格递增
            assertTrue(id > prev, "ID 不递增: prev=" + prev + ", current=" + id);
            prev = id;
        }
        assertEquals(10000, ids.size());
    }

    @Test
    void shouldExtractWorkerIdCorrectly() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(42);
        long id = worker.nextId();
        assertEquals(42, SnowflakeIdWorker.extractWorkerId(id));
    }

    @Test
    void shouldExtractTimestampCorrectly() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1);
        long before = System.currentTimeMillis();
        long id = worker.nextId();
        long after = System.currentTimeMillis();

        long extractedTimestamp = SnowflakeIdWorker.extractTimestamp(id);
        assertTrue(extractedTimestamp >= before,
                "提取时间戳应 >= 生成前时间: " + extractedTimestamp + " < " + before);
        assertTrue(extractedTimestamp <= after,
                "提取时间戳应 <= 生成后时间: " + extractedTimestamp + " > " + after);
    }

    @Test
    void shouldExtractSequenceCorrectly() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1);
        long id = worker.nextId();
        long seq = SnowflakeIdWorker.extractSequence(id);
        assertTrue(seq >= 0 && seq <= 4095,
                "序列号应在 [0, 4095] 范围内: " + seq);
    }

    @Test
    void shouldGenerateDistinctIdsWithDifferentWorkerIds() {
        SnowflakeIdWorker worker0 = new SnowflakeIdWorker(0);
        SnowflakeIdWorker worker1 = new SnowflakeIdWorker(1);

        Set<Long> idsFromWorker0 = new HashSet<>();
        Set<Long> idsFromWorker1 = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            idsFromWorker0.add(worker0.nextId());
            idsFromWorker1.add(worker1.nextId());
        }

        // 不同 worker 生成的 ID 不应有交集
        idsFromWorker0.retainAll(idsFromWorker1);
        assertTrue(idsFromWorker0.isEmpty(), "不同 worker 生成的 ID 不应冲突");
    }

    @Test
    void shouldRejectInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdWorker(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdWorker(1024));
    }
}
