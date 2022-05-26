package com.sf.saas.uid.impl;

import com.sf.saas.uid.BitsAllocator;
import com.sf.saas.uid.UidGenerator;
import com.sf.saas.uid.exception.UidGenerateException;
import com.sf.saas.uid.worker.WorkerIdAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;



import java.util.concurrent.TimeUnit;

/**
 * @author 01407460
 * @date 2022/5/20 17:19
 */
public class DefaultUidGenerator implements UidGenerator, InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUidGenerator.class);

    /** Bits allocate */
    protected int timeBits = 28;
    protected int workerBits = 22;
    protected int seqBits = 13;


    /**
     * 默认开始的 秒
     * 建议设置 系统第一次开始上线的时间
     */
    protected long epochSeconds = TimeUnit.MILLISECONDS.toSeconds(1463673600000L);

    /** Stable fields after spring bean initializing */
    protected BitsAllocator bitsAllocator;
    protected long workerId;

    /** Volatile fields caused by nextId() */
    protected long sequence = 0L;
    protected long lastSecond = -1L;

    /** Spring property */
    protected WorkerIdAllocator workerIdAssigner;

    @Override
    public void afterPropertiesSet() throws Exception {
        // initialize bits allocator
        bitsAllocator = new BitsAllocator(timeBits, workerBits, seqBits);

        // initialize worker id
        workerId = workerIdAssigner.assignWorkerId();
        if (workerId > bitsAllocator.getMaxWorkerId()) {
            throw new RuntimeException("Worker id " + workerId + " exceeds the max " + bitsAllocator.getMaxWorkerId());
        }

        LOGGER.info("Initialized bits(1, {}, {}, {}) for workerID:{}", timeBits, workerBits, seqBits, workerId);
    }

    @Override
    public long getUID() throws UidGenerateException {
        try {
            return nextId();
        } catch (Exception e) {
            LOGGER.error("Generate unique id exception. ", e);
            throw new UidGenerateException(e);
        }
    }

    @Override
    public String parseUID(long uid) {
        long totalBits = BitsAllocator.TOTAL_BITS;
        long signBits = bitsAllocator.getSignBits();
        long timestampBits = bitsAllocator.getTimestampBits();
        long workerIdBits = bitsAllocator.getWorkerIdBits();
        long sequenceBits = bitsAllocator.getSequenceBits();

        // parse UID
        long sequence = (uid << (totalBits - sequenceBits)) >>> (totalBits - sequenceBits);
        long workerId = (uid << (timestampBits + signBits)) >>> (totalBits - workerIdBits);
        long deltaSeconds = uid >>> (workerIdBits + sequenceBits);

        long millis = TimeUnit.SECONDS.toMillis(epochSeconds + deltaSeconds);


        // format as string
        return String.format("{\"UID\":\"%d\",\"timestamp\":\"%d\",\"workerId\":\"%d\",\"sequence\":\"%d\"}",
                uid, millis, workerId, sequence);
    }

    /**
     * Get UID
     *
     * @return UID
     * @throws UidGenerateException in the case: Clock moved backwards; Exceeds the max timestamp
     */
    protected synchronized long nextId() {
        long currentSecond = getCurrentSecond();

        // Clock moved backwards, refuse to generate uid
        if (currentSecond < lastSecond) {
            long refusedSeconds = lastSecond - currentSecond;
            throw new UidGenerateException("Clock moved backwards. Refusing for %d seconds", refusedSeconds);
        }

        // At the same second, increase sequence
        if (currentSecond == lastSecond) {
            sequence = (sequence + 1) & bitsAllocator.getMaxSequence();
            // Exceed the max sequence, we wait the next second to generate uid
            if (sequence == 0) {
                currentSecond = getNextSecond(lastSecond);
            }

            // At the different second, sequence restart from zero
        } else {
            sequence = 0L;
        }

        lastSecond = currentSecond;

        // Allocate bits for UID
        return bitsAllocator.allocate(currentSecond - epochSeconds, workerId, sequence);
    }

    /**
     * Get next millisecond
     */
    private long getNextSecond(long lastTimestamp) {
        long timestamp = getCurrentSecond();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentSecond();
        }

        return timestamp;
    }

    /**
     * Get current second
     */
    private long getCurrentSecond() {
        long currentSecond = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        if (currentSecond - epochSeconds > bitsAllocator.getMaxDeltaSeconds()) {
            throw new UidGenerateException("Timestamp bits is exhausted. Refusing UID generate. Now: " + currentSecond);
        }

        return currentSecond;
    }

    /**
     * Setters for spring property
     */
    public void setWorkerIdAssigner(WorkerIdAllocator workerIdAssigner) {
        this.workerIdAssigner = workerIdAssigner;
    }

    public void setTimeBits(int timeBits) {
        if (timeBits > 0) {
            this.timeBits = timeBits;
        }
    }

    public void setWorkerBits(int workerBits) {
        if (workerBits > 0) {
            this.workerBits = workerBits;
        }
    }

    public void setSeqBits(int seqBits) {
        if (seqBits > 0) {
            this.seqBits = seqBits;
        }
    }

    /**
     * 设置 开始漂移时间  秒
     * @param epochSeconds
     */
    public void setEpochSeconds(long epochSeconds) {

        if (epochSeconds <= 0) {
            throw new RuntimeException("epochSeconds:" + epochSeconds + " expect greater than 0 ");
        }
        //当前 时间的 秒数
        long nowSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

        if (epochSeconds > nowSeconds){
            LOGGER.info("epochSeconds {} greater than current seconds {},so set current seconds", epochSeconds, nowSeconds);
            this.epochSeconds = nowSeconds;
        }else {
            LOGGER.info("epochSeconds set={} ",epochSeconds);
            this.epochSeconds = epochSeconds;
        }

    }
}

