package com.docflow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.docflow.document.LineItem;
import com.docflow.document.Severity;
import com.docflow.document.ValidationIssue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvoiceValidatorTest {

    private final InvoiceValidator validator = new InvoiceValidator("PO-\\d{9}");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    private static Map<String, String> clean() {
        Map<String, String> v = new HashMap<>();
        v.put("vendor_name", "Nordwerk Logistik GmbH");
        v.put("vendor_vat_id", "DE287451903");
        v.put("invoice_number", "INV-2026-020001");
        v.put("invoice_date", "2026-11-20");
        v.put("po_number", "PO-450099001");
        v.put("net_amount", "604.80");
        v.put("vat_amount", "114.91");
        v.put("gross_amount", "719.71");
        v.put("due_date", "2026-12-20");
        return v;
    }

    private static List<LineItem> lines() {
        return List.of(new LineItem("Transport", 3, new BigDecimal("180.00"), new BigDecimal("540.00")),
                new LineItem("Labels", 2, new BigDecimal("32.40"), new BigDecimal("64.80")));
    }

    @Test
    void aCorrectInvoiceHasNoIssues() {
        assertThat(validator.validate(clean(), lines(), TODAY)).allSatisfy((k, v) -> assertThat(v).isEmpty());
    }

    @Test
    void totalsThatDoNotAddUpAreCritical() {
        Map<String, String> v = clean();
        v.put("vat_amount", "124.91");
        List<ValidationIssue> issues = validator.validate(v, lines(), TODAY).get("vat_amount");
        assertThat(issues).extracting(ValidationIssue::getRule).contains("TOTALS_CONSISTENT", "VAT_RATE");
        assertThat(issues).filteredOn(i -> i.getRule().equals("TOTALS_CONSISTENT"))
                .allMatch(i -> i.getSeverity() == Severity.CRITICAL && i.getMessage().contains("10.00"));
    }

    @Test
    void missingRequiredFieldsAreCritical() {
        Map<String, String> v = clean();
        v.put("invoice_number", null);
        assertThat(validator.validate(v, lines(), TODAY).get("invoice_number"))
                .extracting(ValidationIssue::getRule).containsExactly("REQUIRED");
    }

    @Test
    void germanVatIdsNeedNineDigits() {
        Map<String, String> v = clean();
        v.put("vendor_vat_id", "DE28745190");
        assertThat(validator.validate(v, lines(), TODAY).get("vendor_vat_id"))
                .extracting(ValidationIssue::getRule).containsExactly("VAT_ID_FORMAT");
    }

    @Test
    void letterOInAPoNumberIsRecognisedAsAReadingSlip() {
        Map<String, String> v = clean();
        v.put("po_number", "PO-45OO99001");
        List<ValidationIssue> issues = validator.validate(v, lines(), TODAY).get("po_number");
        assertThat(issues).singleElement().satisfies(i -> assertThat(i.getMessage()).contains("Letter \"O\""));
    }

    @Test
    void lineItemsMustAddUpToNet() {
        Map<String, String> v = clean();
        List<LineItem> wrong = List.of(new LineItem("Transport", 3, new BigDecimal("180.00"), new BigDecimal("540.00")));
        assertThat(validator.validate(v, wrong, TODAY).get("net_amount"))
                .extracting(ValidationIssue::getRule).containsExactly("LINE_ITEMS_SUM");
    }

    @Test
    void dueDateBeforeInvoiceDateIsAWarning() {
        Map<String, String> v = clean();
        v.put("due_date", "2026-11-01");
        assertThat(validator.validate(v, lines(), TODAY).get("due_date"))
                .extracting(ValidationIssue::getRule).containsExactly("DUE_BEFORE_INVOICE");
    }
}
