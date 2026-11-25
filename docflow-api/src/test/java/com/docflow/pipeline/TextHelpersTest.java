package com.docflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TextHelpersTest {

    @Test
    void amountsInEnglishAndGermanNotation() {
        assertThat(Amounts.parse("1,234.56")).contains(new BigDecimal("1234.56"));
        assertThat(Amounts.parse("1.234,56")).contains(new BigDecimal("1234.56"));
        assertThat(Amounts.parse("719.71 EUR")).contains(new BigDecimal("719.71"));
        assertThat(Amounts.parse("€ 99,00")).contains(new BigDecimal("99.00"));
        assertThat(Amounts.parse("1,000")).contains(new BigDecimal("1000.00"));
        assertThat(Amounts.parse("n/a")).isEmpty();
    }

    @Test
    void redactionRemovesBankDetailsMailAndPhoneButKeepsInvoiceData() {
        String text = "IBAN DE89 3704 0044 0532 0130 00, billing@example.com, +49 6227 123456, "
                + "Date: 2026-11-20, VAT ID: DE287451903, PO-450099001, Total 719.71";
        String out = Redactor.redact(text);
        assertThat(out).contains("[IBAN]", "[EMAIL]", "[PHONE]", "2026-11-20", "DE287451903", "PO-450099001", "719.71")
                .doesNotContain("3704", "billing@", "6227");
    }
}
