// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.apiguardian.api.API;

/**
 * 四类 CRUD 会话执行的计数器。
 *
 * <p>所有操作均为原子操作 &mdash; 结构上保证线程安全。该类型仅包含 <em>long</em>
 * 计数器;耗时以微秒计,外加一个供框架内部使用的 {@code complexQuery} 通道,
 * 供范围查询路径进行区分归类。
 *
 * @author JadenDu
 */
@API(status = API.Status.EXPERIMENTAL)
public final class QueryStatistics {

    private final AtomicLong inserts = new AtomicLong();
    private final AtomicLong batchInserts = new AtomicLong();
    private final AtomicLong updates = new AtomicLong();
    private final AtomicLong deletes = new AtomicLong();
    private final AtomicLong selects = new AtomicLong();
    private final AtomicLong countQueries = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong elapsedMicros = new AtomicLong();

    public long inserts() {
        return inserts.get();
    }

    public long batchInserts() {
        return batchInserts.get();
    }

    public long updates() {
        return updates.get();
    }

    public long deletes() {
        return deletes.get();
    }

    public long selects() {
        return selects.get();
    }

    public long countQueries() {
        return countQueries.get();
    }

    public long errors() {
        return errors.get();
    }

    public long micros() {
        return elapsedMicros.get();
    }

    public void recordInsert(long eMicros) {
        inserts.incrementAndGet();
        elapsedMicros.addAndGet(eMicros);
    }

    public void recordBatchInsert(long eMicros) {
        batchInserts.incrementAndGet();
        elapsedMicros.addAndGet(eMicros);
    }

    public void recordUpdate(long eMicros) {
        updates.incrementAndGet();
        elapsedMicros.addAndGet(eMicros);
    }

    public void recordDelete(long eMicros) {
        deletes.incrementAndGet();
        elapsedMicros.addAndGet(eMicros);
    }

    public void recordSelect(long eMicros) {
        selects.incrementAndGet();
        elapsedMicros.addAndGet(eMicros);
    }

    public void recordCount(long eMicros) {
        countQueries.incrementAndGet();
        elapsedMicros.addAndGet(eMicros);
    }

    public void recordError(long eMicros) {
        errors.incrementAndGet();
        elapsedMicros.addAndGet(eMicros);
    }

    public void reset() {
        inserts.set(0);
        batchInserts.set(0);
        updates.set(0);
        deletes.set(0);
        selects.set(0);
        countQueries.set(0);
        errors.set(0);
        elapsedMicros.set(0);
    }

    @Override
    public String toString() {
        return "QueryStatistics{inserts="
                + inserts
                + ",batchInserts="
                + batchInserts
                + ",updates="
                + updates
                + ",deletes="
                + deletes
                + ",selects="
                + selects
                + ",countQueries="
                + countQueries
                + ",errors="
                + errors
                + ",elapsedMicros="
                + elapsedMicros
                + "}";
    }
}
