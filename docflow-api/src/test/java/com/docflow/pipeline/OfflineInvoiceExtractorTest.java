package com.docflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.docflow.pipeline.extract.ExtractedInvoice;
import com.docflow.pipeline.extract.OfflineInvoiceExtractor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OfflineInvoiceExtractorTest {

    @Test
    void extractsEveryFieldOfTheCleanSample() throws Exception {
        String text;
        try (InputStream in = getClass().getResourceAsStream("/samples/sample-invoice-clean.txt")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ExtractedInvoice result = new OfflineInvoiceExtractor().extract(text);

        assertThat(result.fields().get("vendor_name").value()).isEqualTo("Nordwerk Logistik GmbH");
        assertThat(result.fields().get("vendor_vat_id").value()).isEqualTo("DE287451903");
        assertThat(result.fields().get("invoice_number").value()).isEqualTo("INV-2026-020001");
        assertThat(result.fields().get("invoice_date").value()).isEqualTo("2026-11-20");
        assertThat(result.fields().get("po_number").value()).isEqualTo("PO-450099001");
        assertThat(result.fields().get("net_amount").value()).isEqualTo("604.80");
        assertThat(result.fields().get("vat_amount").value()).isEqualTo("114.91");
        assertThat(result.fields().get("gross_amount").value()).isEqualTo("719.71");
        assertThat(result.fields().get("due_date").value()).isEqualTo("2026-12-20");
        assertThat(result.lineItems()).hasSize(2);
        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.inputTokens()).isZero();
    }

    @Test
    void germanDatesBecomeIso() {
        ExtractedInvoice result = new OfflineInvoiceExtractor().extract("ACME GmbH\nDatum: 3.2.2026\nTotal: 100,00 EUR");
        assertThat(result.fields().get("invoice_date").value()).isEqualTo("2026-02-03");
        assertThat(result.fields().get("gross_amount").value()).isEqualTo("100.00");
    }
}
