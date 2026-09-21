/* Copyright 2026 Google LLC */
package anthos.samples.bankofanthos.transactionhistory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Distributed, fail-open cache-aside storage for recent transactions. */
@Component
public class TransactionCache {
    private static final Logger LOGGER = LogManager.getLogger(TransactionCache.class);
    private static final String KEY_PREFIX = "boa:cache:v1:history:";
    private static final TypeReference<LinkedList<Transaction>> HISTORY_TYPE =
        new TypeReference<LinkedList<Transaction>>() { };

    private final TransactionRepository dbRepo;
    private final ObjectMapper objectMapper;
    private final String localRoutingNum;
    private final int historyLimit;
    private final long ttlSeconds;
    private final long jitterSeconds;
    private final RedisClusterClient client;
    private StatefulRedisClusterConnection<String, String> connection;

    public TransactionCache(
            TransactionRepository dbRepo,
            ObjectMapper objectMapper,
            @Value("${LOCAL_ROUTING_NUM}") String localRoutingNum,
            @Value("${HISTORY_LIMIT:100}") int historyLimit,
            @Value("${HISTORY_CACHE_TTL_SECONDS:30}") long ttlSeconds,
            @Value("${HISTORY_CACHE_JITTER_SECONDS:3}") long jitterSeconds,
            @Value("${VALKEY_HOST:}") String host,
            @Value("${VALKEY_PORT:6379}") int port,
            @Value("${VALKEY_USERNAME:}") String username,
            @Value("${VALKEY_PASSWORD:}") String password,
            @Value("${VALKEY_TLS:true}") boolean useTls) {
        this.dbRepo = dbRepo;
        this.objectMapper = objectMapper;
        this.localRoutingNum = localRoutingNum;
        this.historyLimit = historyLimit;
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
    public Deque<Transaction> get(String accountId) {
        try {
            String cached = commands().get(key(accountId));
            if (cached != null) {
                return objectMapper.readValue(cached, HISTORY_TYPE);
            }
        } catch (RuntimeException | JsonProcessingException err) {
            LOGGER.warn("History cache read failed; using DB: {}", err.toString());
        }

        Pageable request = PageRequest.of(0, historyLimit);
        Deque<Transaction> history = dbRepo.findForAccount(
            accountId, localRoutingNum, request);
        try {
            String value = objectMapper.writeValueAsString(history);
            commands().setex(key(accountId), randomizedTtl(), value);
        } catch (RuntimeException | JsonProcessingException err) {
            LOGGER.warn("History cache write failed: {}", err.toString());
        }
        return history;
    }

    /** Delete after ledger changes; failure does not roll back a DB commit. */
    public void invalidate(String accountId) {
        try {
            commands().del(key(accountId));
        } catch (RuntimeException err) {
            LOGGER.warn("History cache invalidation failed: {}", err.toString());
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
