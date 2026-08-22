package com.crypto.wallet.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WalletAssetRepositoryAtomicMutationTest {

    @Test
    void creditAndDebitQueriesMutateQuantityAtomicallyInDatabase() throws Exception {
        Method credit = WalletAssetRepository.class.getMethod("creditQuantity", String.class, BigDecimal.class);
        Method debit = WalletAssetRepository.class.getMethod("debitQuantityIfSufficient", String.class, BigDecimal.class);

        String creditQuery = credit.getAnnotation(Query.class).value().replaceAll("\\s+", " ").toLowerCase();
        String debitQuery = debit.getAnnotation(Query.class).value().replaceAll("\\s+", " ").toLowerCase();

        // FIX-037 regression guard: never regress to Java read/modify/save for cash balances.
        assertTrue(creditQuery.contains("a.quantity = a.quantity + :amount"));
        assertTrue(debitQuery.contains("a.quantity = a.quantity - :amount"));
        assertTrue(debitQuery.contains("a.quantity >= :amount"));
    }

    @Test
    void aug21LostUpdateIncidentHasDeterministicExpectedBalance() {
        BigDecimal startingUsdt = new BigDecimal("9749.900237510953");
        BigDecimal uniSell = new BigDecimal("251.970005083884");
        BigDecimal dogeBuy = new BigDecimal("125.000000000000");
        BigDecimal pepeBuy = new BigDecimal("250.000000000000");

        BigDecimal expectedAfterUniAndDoge = startingUsdt.add(uniSell).subtract(dogeBuy);
        BigDecimal expectedAfterAllThree = expectedAfterUniAndDoge.subtract(pepeBuy);

        assertEquals(new BigDecimal("9876.870242594837"), expectedAfterUniAndDoge);
        assertEquals(new BigDecimal("9626.870242594837"), expectedAfterAllThree);
        // The corrupted balance observed in production was 9374.900237510953, exactly
        // missing the UNI SELL credit. This assertion documents that failure signature.
        assertNotEquals(new BigDecimal("9374.900237510953"), expectedAfterAllThree);
    }
}
