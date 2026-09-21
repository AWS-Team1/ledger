/* Copyright 2026 Google LLC */
package anthos.samples.bankofanthos.ledgerwriter;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Best-effort invalidation after a ledger transaction commits. */
@Component
public class LedgerCacheInvalidator {
    private static final Logger LOGGER =
        LogManager.getLogger(LedgerCacheInvalidator.class);
    private static final String BALANCE_PREFIX = "boa:cache:v1:balance:";
    private static final String HISTORY_PREFIX = "boa:cache:v1:history:";

    private final RedisClusterClient client;
    private StatefulRedisClusterConnection<String, String> connection;

    public LedgerCacheInvalidator(
            @Value("${VALKEY_HOST:}") String host,
            @Value("${VALKEY_PORT:6379}") int port,
            @Value("${VALKEY_USERNAME:}") String username,
            @Value("${VALKEY_PASSWORD:}") String password,
            @Value("${VALKEY_TLS:true}") boolean useTls) {
        this.client = host.isBlank() ? null : RedisClusterClient.create(
            RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withAuthentication(username, password.toCharArray())
                .withSsl(useTls)
                .withTimeout(Duration.ofSeconds(2))
                .build());
    }

    /** Invalidate balance and history keys in the same cluster hash slot. */
    public void invalidate(String accountId) {
        String tag = "{" + accountId + "}";
        try {
            commands().del(BALANCE_PREFIX + tag, HISTORY_PREFIX + tag);
        } catch (RuntimeException err) {
            LOGGER.warn("Ledger cache invalidation failed: {}", err.toString());
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
