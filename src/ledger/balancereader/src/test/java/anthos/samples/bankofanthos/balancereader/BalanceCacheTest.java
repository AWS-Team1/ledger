/* Copyright 2026 Google LLC */
package anthos.samples.bankofanthos.balancereader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class BalanceCacheTest {
    @Test
    void fallsBackToDatabaseWhenValkeyIsNotConfigured() {
        TransactionRepository repository = mock(TransactionRepository.class);
        when(repository.findBalance("1234567890", "123456789"))
            .thenReturn(2500L);
        BalanceCache cache = new BalanceCache(
            repository, "123456789", 5, 1, "", 6379, "", "", true);

        assertEquals(2500L, cache.get("1234567890"));
    }
}
