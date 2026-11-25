package com.docflow.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Parses money amounts as they appear on invoices: "1,234.56", "1.234,56", "1234.56 EUR", "€ 99,00".
 * The last separator followed by exactly two digits is the decimal separator.
 */
public final class Amounts {

    private Amounts() {
    }

    public static Optional<BigDecimal> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.replaceAll("[^0-9,.\\-]", "");
        if (s.isEmpty() || s.equals("-")) {
            return Optional.empty();
        }
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        int sep = Math.max(lastDot, lastComma);
        String normalized;
        if (sep >= 0 && s.length() - sep - 1 == 2) {
            String whole = s.substring(0, sep).replace(".", "").replace(",", "");
            normalized = whole + "." + s.substring(sep + 1);
        } else {
            normalized = s.replace(".", "").replace(",", "");
        }
        try {
            return Optional.of(new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Canonical form used for stored values: plain number with two decimals, e.g. "1234.56". */
    public static String canonical(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
