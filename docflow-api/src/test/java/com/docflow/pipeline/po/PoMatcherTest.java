package com.docflow.pipeline.po;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PoMatcherTest {

    private final PurchaseOrder po = new PurchaseOrder("1", "acme-de", "PO-450099003", "Rheintal Office Supply",
            new BigDecimal("761.60"), "EUR");

    @Test
    void exactMatchScoresOneWithNoDifferences() {
        PoMatcher.Result r = PoMatcher.score(po, "PO-450099003", "Rheintal Office Supply", new BigDecimal("761.60"));
        assertThat(r.score()).isEqualTo(1.0);
        assertThat(r.deltas()).isEmpty();
    }

    @Test
    void letterOAndMissingLegalFormStillMatch() {
        PoMatcher.Result r = PoMatcher.score(po, "PO-45OO99003", "Rheintal Office Supply GmbH", new BigDecimal("761.60"));
        assertThat(r.score()).isGreaterThan(0.95);
        assertThat(r.deltas()).singleElement().asString().contains("differs from the purchase order");
    }

    @Test
    void aWrongPoNumberIsFoundByVendorAndAmountButFlagged() {
        PoMatcher.Result r = PoMatcher.score(po, "PO-450099999", "Rheintal Office Supply", new BigDecimal("761.60"));
        assertThat(r.score()).isBetween(PoMatcher.MIN_SCORE, 0.9);
        assertThat(r.deltas()).isNotEmpty();
    }

    @Test
    void anUnrelatedInvoiceDoesNotMatch() {
        PoMatcher.Result r = PoMatcher.score(po, "PO-450011111", "Nordwerk Logistik GmbH", new BigDecimal("12.00"));
        assertThat(r.score()).isLessThan(PoMatcher.MIN_SCORE);
    }

    @Test
    void similarityIsNormalisedLevenshtein() {
        assertThat(PoMatcher.similarity("abc", "abc")).isEqualTo(1.0);
        assertThat(PoMatcher.similarity("abc", "abd")).isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
    }
}
