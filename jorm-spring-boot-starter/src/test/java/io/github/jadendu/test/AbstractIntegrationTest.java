// SPDX-License-Identifier: Apache-2.0
package io.github.jadendu.test;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;

import redis.embedded.RedisServer;
import redis.embedded.RedisServerBuilder;

@SpringBootTest(classes = TestApplication.class)
public abstract class AbstractIntegrationTest {

    private static RedisServer redisServer;

    @BeforeAll
    static void startRedis() throws Exception {
        RedisServerBuilder builder = RedisServer.builder().port(6379);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            builder.setting("maxheap 64mb");
        }
        redisServer = builder.build();
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
