// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.cache.impl;

import io.github.jadendu.cache.SecondLevelCache;

public class NoOpSecondLevelCache implements SecondLevelCache {
    @Override
    public void put(String region, String key, Object value) {
        // 空实现，不做任何操作
    }

    @Override
    public Object get(String region, String key) {
        return null; // 总是返回 null
    }

    @Override
    public void remove(String region, String key) {
        // 空实现
    }

    @Override
    public void clearRegion(String region) {
        // 空实现
    }

    @Override
    public void clearAll() {
        // 空实现
    }
}
