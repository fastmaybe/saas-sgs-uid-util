package com.sf.saas.uid.worker;

/**
 * @author 01407460
 * @date 2022/5/20 17:34
 */
public interface WorkerIdAllocator {


    /**
     * 分配workerID 唯一 留给自己实现 需要
     * @return
     */
    long assignWorkerId();
}
