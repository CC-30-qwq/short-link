package com.example.shortlink.util;

import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法分布式ID生成器
 * <p>
 * 64位结构:
 * [1位保留=0] [41位时间戳(毫秒)] [5位数据中心ID] [5位工作节点ID] [12位序列号]
 * <p>
 * 支持约69年运行，每毫秒每节点4096个ID
 */
@Slf4j
@Component
public class SnowflakeIdGenerator {

    /** 自定义纪元: 2024-01-01 00:00:00 */
    private static final long EPOCH = 1704067200000L;

    /** 各部分位数 */
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    /** 最大值 */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);       // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);          // 4095

    /** 左移位数 */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long DATACENTER_ID_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long WORKER_ID_LEFT_SHIFT = SEQUENCE_BITS;

    /** 工作节点ID */
    private final long workerId;

    /** 数据中心ID */
    private final long datacenterId;

    /** 毫秒内序列 */
    private long sequence = 0L;

    /** 上次生成ID的时间戳 */
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(
            @Value("${shortlink.snowflake.worker-id}") long workerId,
            @Value("${shortlink.snowflake.datacenter-id}") long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("workerId 必须在 [0, %d] 之间，当前值: %d", MAX_WORKER_ID, workerId));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("datacenterId 必须在 [0, %d] 之间，当前值: %d", MAX_DATACENTER_ID, datacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("雪花算法初始化完成: workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /**
     * 生成下一个全局唯一ID
     */
    public synchronized long nextId() {
        long currentMillis = System.currentTimeMillis();

        // 时钟回拨检测
        if (currentMillis < lastTimestamp) {
            long offset = lastTimestamp - currentMillis;
            log.error("时钟回拨 {}ms，拒绝生成ID", offset);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统时钟异常，请稍后重试");
        }

        if (currentMillis == lastTimestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号耗尽，等待下一毫秒
                currentMillis = waitNextMillis(lastTimestamp);
            }
        } else {
            // 新的一毫秒，序列号归零
            sequence = 0L;
        }

        lastTimestamp = currentMillis;

        return ((currentMillis - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_LEFT_SHIFT)
                | (workerId << WORKER_ID_LEFT_SHIFT)
                | sequence;
    }

    /**
     * 自旋等待直到下一毫秒
     */
    private long waitNextMillis(long lastTimestamp) {
        long currentMillis = System.currentTimeMillis();
        while (currentMillis <= lastTimestamp) {
            currentMillis = System.currentTimeMillis();
        }
        return currentMillis;
    }
}
