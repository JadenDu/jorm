// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;

import redis.embedded.RedisServer;

@SpringBootTest(classes = TestApplication.class)
public abstract class AbstractIntegrationTest {

    private static RedisServer redisServer;

    @BeforeAll
    static void startRedis() throws Exception {
        redisServer = RedisServer.builder().port(6379).setting("maxheap 64mb").build();
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
