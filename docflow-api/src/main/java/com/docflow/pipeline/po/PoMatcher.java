package com.docflow.pipeline.po;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 4: finds the purchase order an invoice belongs to.
 * Fuzzy, because invoices are messy: "PO-45O099001" (letter O) or "Nordwerk Logistik" without "GmbH"
 * should still match. Score = 50% PO number + 30% vendor name + 20% amount.
 */
@Component
public class PoMatcher {

    /** Below this score there is no match at all. */
    static final double MIN_SCORE = 0.6;

    public record Result(PurchaseOrder po, double score, List<String> deltas) {
    }

    private final PurchaseOrderRepository orders;

    public PoMatcher(PurchaseOrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public Optional<Result> match(String tenantId, String poNumber, String vendor, BigDecimal gross) {
        // Fine for a demo; with many POs this would query by vendor or number first.
        return orders.findByTenantId(tenantId).stream()
                .map(po -> score(po, poNumber, vendor, gross))
                .filter(r -> r.score() >= MIN_SCORE)
                .max(Comparator.comparingDouble(Result::score));
    }

    static Result score(PurchaseOrder po, String poNumber, String vendor, BigDecimal gross) {
        double number = poNumber == null ? 0 : similarity(normalizePo(poNumber), normalizePo(po.getPoNumber()));
        double name = vendor == null ? 0 : similarity(normalizeName(vendor), normalizeName(po.getVendor()));
        double amount = 0;
        if (gross != null && po.getAmount().signum() > 0) {
            double diff = gross.subtract(po.getAmount()).abs().doubleValue() / po.getAmount().doubleValue();
            amount = Math.max(0, 1 - diff * 10);     // 10% off → 0
        }
        double score = Math.round((0.5 * number + 0.3 * name + 0.2 * amount) * 1000) / 1000.0;

        List<String> deltas = new ArrayList<>();
        if (poNumber == null) {
            deltas.add("The invoice has no PO number; matched by vendor and amount.");
        } else if (!poNumber.equals(po.getPoNumber())) {
            deltas.add("PO number on the invoice (" + poNumber + ") differs from the purchase order (" + po.getPoNumber() + ").");
        }
        if (vendor != null && name < 0.9) {
            deltas.add("Vendor differs: invoice says \"" + vendor + "\", purchase order says \"" + po.getVendor() + "\".");
        }
        if (gross != null && gross.subtract(po.getAmount()).abs().compareTo(new BigDecimal("0.01")) > 0) {
            deltas.add("Gross amount differs from PO by " + gross.subtract(po.getAmount()).abs().setScale(2, RoundingMode.HALF_UP)
                    + " " + po.getCurrency());
        }
        return new Result(po, score, deltas);
    }

    /** Letter O is read as digit 0; spaces and hyphens are ignored. */
    static String normalizePo(String s) {
        return s.toUpperCase(Locale.ROOT).replace('O', '0').replaceAll("[\\s-]", "");
    }

    /** Lower case, legal forms (GmbH, AG ...) and punctuation removed. */
    static String normalizeName(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(gmbh|ag|kg|se|ug|e\\.?k\\.?|ltd|inc|llc|co)\\b", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** 1 - normalised Levenshtein distance. */
    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return 1.0 - (double) prev[b.length()] / Math.max(a.length(), b.length());
    }
}
