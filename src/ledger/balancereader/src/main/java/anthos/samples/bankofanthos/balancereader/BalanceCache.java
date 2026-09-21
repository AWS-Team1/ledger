/* Copyright 2026 Google LLC */
package anthos.samples.bankofanthos.balancereader;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Distributed, fail-open cache-aside storage for display balances. */
@Component
public class BalanceCache {
    private static final Logger LOGGER = LogManager.getLogger(BalanceCache.class);
    private static final String KEY_PREFIX = "boa:cache:v1:balance:";

    private final TransactionRepository dbRepo;
    private final String localRoutingNum;
    private final long ttlSeconds;
    private final long jitterSeconds;
    private final RedisClusterClient client;
    private StatefulRedisClusterConnection<String, String> connection;

    public BalanceCache(
            TransactionRepository dbRepo,
            @Value("${LOCAL_ROUTING_NUM}") String localRoutingNum,
            @Value("${BALANCE_CACHE_TTL_SECONDS:5}") long ttlSeconds,
            @Value("${BALANCE_CACHE_JITTER_SECONDS:1}") long jitterSeconds,
            @Value("${VALKEY_HOST:}") String host,
            @Value("${VALKEY_PORT:6379}") int port,
            @Value("${VALKEY_USERNAME:}") String username,
            @Value("${VALKEY_PASSWORD:}") String password,
            @Value("${VALKEY_TLS:true}") boolean useTls) {
        this.dbRepo = dbRepo;
        this.localRoutingNum = localRoutingNum;
        this.ttlSeconds = ttlSeconds;
        this.jitterSeconds = jitterSeconds;
        this.client = host.isBlank() ? null : RedisClusterClient.create(
            RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withAuthentication(username, password.toCharArray())
                .withSsl(useTls)
                .withTimeout(Duration.ofSeconds(2))
                .build());
    }

    /** Get from Valkey, falling back to DB on miss or cache failure. */
    public Long get(String accountId) {
        try {
            String cached = commands().get(key(accountId));
            if (cached != null) {
                return Long.valueOf(cached);
            }
        } catch (RuntimeException err) {
            LOGGER.warn("Balance cache read failed; using DB: {}", err.toString());
        }

        Long balance = dbRepo.findBalance(accountId, localRoutingNum);
        if (balance == null) {
            balance = 0L;
        }
        try {
            commands().setex(key(accountId), randomizedTtl(), balance.toString());
        } catch (RuntimeException err) {
            LOGGER.warn("Balance cache write failed: {}", err.toString());
        }
        return balance;
    }

    /** Delete after ledger changes; failure does not roll back a DB commit. */
    public void invalidate(String accountId) {
        try {
            commands().del(key(accountId));
        } catch (RuntimeException err) {
            LOGGER.warn("Balance cache invalidation failed: {}", err.toString());
        }
    }

    private synchronized RedisAdvancedClusterCommands<String, String> commands() {
        if (client == null) {
            throw new IllegalStateException("Valkey cache is not configured");
        }
        if (connection == null || !connection.isOpen()) {
            connection = client.connect();
        }
        return connection.sync();
    }

    private long randomizedTtl() {
        return ttlSeconds + ThreadLocalRandom.current()
            .nextLong(jitterSeconds + 1);
    }

    private String key(String accountId) {
        return KEY_PREFIX + "{" + accountId + "}";
    }

    @PreDestroy
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }
}
