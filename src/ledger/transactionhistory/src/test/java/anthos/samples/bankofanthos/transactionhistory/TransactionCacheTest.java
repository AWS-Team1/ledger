/* Copyright 2026 Google LLC */
package anthos.samples.bankofanthos.transactionhistory;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedList;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class TransactionCacheTest {
    @Test
    void fallsBackToDatabaseWhenValkeyIsNotConfigured() {
        TransactionRepository repository = mock(TransactionRepository.class);
        LinkedList<Transaction> history = new LinkedList<>();
        when(repository.findForAccount(
            org.mockito.ArgumentMatchers.eq("1234567890"),
            org.mockito.ArgumentMatchers.eq("123456789"),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(history);
        TransactionCache cache = new TransactionCache(
            repository, new ObjectMapper(), "123456789", 100,
            30, 3, "", 6379, "", "", true);

        assertSame(history, cache.get("1234567890"));
    }
}
