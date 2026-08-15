// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.apiguardian.api.API;

/**
 * Counters for L2 cache behaviour. Populated mostly through the {@link
 * io.github.jadendu.cache.MeasuringSecondLevelCache} decorator, which is applied automatically by
 * {@link io.github.jadendu.cache.CacheManager}.
 *
 * @author JadenDu
 */
@API(status = API.Status.EXPERIMENTAL)
public final class CacheStatistics {

    private final AtomicLong gets = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong removes = new AtomicLong();
    private final AtomicLong regionClears = new AtomicLong();
    private final AtomicLong allClears = new AtomicLong();

    public long gets() {
        return gets.get();
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long puts() {
        return puts.get();
    }

    public long removes() {
        return removes.get();
    }

    public long regionClears() {
        return regionClears.get();
    }

    public long allClears() {
        return allClears.get();
    }

    public double hitRate() {
        long g = gets.get();
        return g == 0 ? 0.0 : ((double) hits.get()) / g;
    }

    public void recordGet(boolean hit) {
        gets.incrementAndGet();
        if (hit) hits.incrementAndGet();
        else misses.incrementAndGet();
    }

    public void recordPut() {
        puts.incrementAndGet();
    }

    public void recordRemove() {
        removes.incrementAndGet();
    }

    public void recordRegionClear() {
        regionClears.incrementAndGet();
    }

    public void recordAllClear() {
        allClears.incrementAndGet();
    }

    public void reset() {
        gets.set(0);
        hits.set(0);
        misses.set(0);
        puts.set(0);
        removes.set(0);
        regionClears.set(0);
        allClears.set(0);
    }

    @Override
    public String toString() {
        return "CacheStatistics{gets="
                + gets
                + ",hits="
                + hits
                + ",misses="
                + misses
                + ",hitRate="
                + String.format("%.3f", hitRate())
                + ",puts="
                + puts
                + ",removes="
                + removes
                + ",regionClears="
                + regionClears
                + "}";
    }
}
