package lanshan.manmu.common.util;

/**
 * Snowflake 分布式 ID 生成器。
 *
 * <p>位布局（64 bit）:
 * <ul>
 *   <li>1 bit  : 符号位（0）</li>
 *   <li>41 bit : 时间戳（毫秒，自 Epoch 起算），可用 ~69 年</li>
 *   <li>10 bit : workerId（0~1023），区分不同服务实例</li>
 *   <li>12 bit : 序列号（0~4095），同一毫秒内递增</li>
 * </ul>
 *
 * <p>Epoch = 2024-01-01 00:00:00 UTC，不用 Twitter 原值。
 */
public class SnowflakeIdWorker {

    /** 起始时间戳：2024-01-01 00:00:00 UTC (毫秒) */
    private static final long EPOCH = 1704067200000L;

    /** workerId 占用位数 */
    private static final long WORKER_ID_BITS = 10L;
    /** 序列号占用位数 */
    private static final long SEQUENCE_BITS  = 12L;

    /** workerId 最大值 = 1023 */
    private static final long MAX_WORKER_ID  = ~(-1L << WORKER_ID_BITS);
    /** 序列号最大值 = 4095 */
    private static final long SEQUENCE_MASK  = ~(-1L << SEQUENCE_BITS);

    /** workerId 左移位数 = 12 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 时间戳左移位数 = 12 + 10 = 22 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * @param workerId 工作节点 ID (0~1023)，不同服务实例必须不同
     */
    public SnowflakeIdWorker(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID)
            throw new IllegalArgumentException(
                "workerId must be in [0, " + MAX_WORKER_ID + "], got " + workerId);
        this.workerId = workerId;
    }

    /** 生成下一个 ID（线程安全） */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨检测
        if (timestamp < lastTimestamp)
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id for "
                + (lastTimestamp - timestamp) + "ms");

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0)
                timestamp = tilNextMillis(lastTimestamp);
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
             | (workerId << WORKER_ID_SHIFT)
             | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp)
            timestamp = System.currentTimeMillis();
        return timestamp;
    }

    /** 从 ID 中提取时间戳（毫秒，自 Epoch 起） */
    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    /** 从 ID 中提取 workerId */
    public static long extractWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /** 从 ID 中提取序列号 */
    public static long extractSequence(long id) {
        return id & SEQUENCE_MASK;
    }
}
