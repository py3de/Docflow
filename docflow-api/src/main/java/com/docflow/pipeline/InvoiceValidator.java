package com.docflow.pipeline;

import com.docflow.document.LineItem;
import com.docflow.document.Severity;
import com.docflow.document.ValidationIssue;
import com.docflow.pipeline.FieldCatalog.FieldDef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Stage 3: business rules, in plain Java. An LLM can misread a number; these rules catch it.
 * Each rule attaches an issue to the field it concerns. Critical issues block approval in the UI.
 */
public final class InvoiceValidator {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final List<BigDecimal> GERMAN_VAT_RATES = List.of(new BigDecimal("0.19"), new BigDecimal("0.07"), BigDecimal.ZERO);
    private static final Pattern GERMAN_VAT_ID = Pattern.compile("DE\\d{9}");
    private static final Pattern EU_VAT_ID = Pattern.compile("[A-Z]{2}[A-Z0-9]{2,13}");

    private final Pattern poPattern;

    public InvoiceValidator(String poNumberPattern) {
        this.poPattern = Pattern.compile(poNumberPattern);
    }

    /**
     * @param values field key → extracted value (null when not found)
     * @param lines  extracted line items
     * @param today  for the "invoice date in the future" check
     */
    public Map<String, List<ValidationIssue>> validate(Map<String, String> values, List<LineItem> lines, LocalDate today) {
        Map<String, List<ValidationIssue>> issues = new HashMap<>();

        for (FieldDef def : FieldCatalog.FIELDS) {
            String v = values.get(def.key());
            if (v == null) {
                if (def.required()) {
                    add(issues, def.key(), "REQUIRED", Severity.CRITICAL, "Not found in the document.");
                }
                continue;
            }
            if (def.kind() == FieldCatalog.Kind.AMOUNT && Amounts.parse(v).isEmpty()) {
                add(issues, def.key(), "AMOUNT_FORMAT", Severity.CRITICAL, "\"" + v + "\" is not an amount.");
            }
            if (def.kind() == FieldCatalog.Kind.DATE && date(v).isEmpty()) {
                add(issues, def.key(), "DATE_ISO", Severity.WARNING, "\"" + v + "\" is not a valid date (expected YYYY-MM-DD).");
            }
        }

        Optional<BigDecimal> net = amount(values, "net_amount");
        Optional<BigDecimal> vat = amount(values, "vat_amount");
        Optional<BigDecimal> gross = amount(values, "gross_amount");

        if (net.isPresent() && vat.isPresent() && gross.isPresent()) {
            BigDecimal diff = net.get().add(vat.get()).subtract(gross.get());
            if (diff.abs().compareTo(TOLERANCE) > 0) {
                add(issues, "vat_amount", "TOTALS_CONSISTENT", Severity.CRITICAL,
                        "Net + VAT does not equal gross (off by " + diff.abs().setScale(2, RoundingMode.HALF_UP) + ").");
            }
        }
        if (net.isPresent() && vat.isPresent() && net.get().signum() > 0) {
            BigDecimal rate = vat.get().divide(net.get(), 4, RoundingMode.HALF_UP);
            boolean known = GERMAN_VAT_RATES.stream().anyMatch(r -> rate.subtract(r).abs().compareTo(new BigDecimal("0.002")) <= 0);
            if (!known) {
                add(issues, "vat_amount", "VAT_RATE", Severity.WARNING,
                        "VAT is " + rate.movePointRight(2).setScale(1, RoundingMode.HALF_UP) + "% of net; expected 19% or 7%.");
            }
        }
        if (net.isPresent() && !lines.isEmpty() && lines.stream().allMatch(l -> l.getTotal() != null)) {
            BigDecimal sum = lines.stream().map(LineItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.subtract(net.get()).abs().compareTo(TOLERANCE) > 0) {
                add(issues, "net_amount", "LINE_ITEMS_SUM", Severity.WARNING,
                        "Line items add up to " + sum.setScale(2, RoundingMode.HALF_UP) + ", not to the net amount.");
            }
        }

        String vatId = values.get("vendor_vat_id");
        if (vatId != null) {
            String compact = vatId.replace(" ", "").toUpperCase();
            boolean ok = compact.startsWith("DE") ? GERMAN_VAT_ID.matcher(compact).matches() : EU_VAT_ID.matcher(compact).matches();
            if (!ok) {
                add(issues, "vendor_vat_id", "VAT_ID_FORMAT", Severity.CRITICAL,
                        compact.startsWith("DE") ? "German VAT IDs have 9 digits after \"DE\"." : "Not a valid EU VAT ID format.");
            }
        }

        String po = values.get("po_number");
        if (po != null && !poPattern.matcher(po).matches()) {
            // Only an O next to a digit is a likely slip; the O in the "PO-" prefix is meant.
            boolean ocrSlip = poPattern.matcher(po.replaceAll("(?<=\\d)[Oo]|[Oo](?=\\d)", "0")).matches();
            add(issues, "po_number", "PO_FORMAT", Severity.WARNING, ocrSlip
                    ? "Letter \"O\" found where a digit is expected — likely an OCR or reading slip."
                    : "Does not match this company's purchase-order format.");
        }

        Optional<LocalDate> invoiceDate = Optional.ofNullable(values.get("invoice_date")).flatMap(InvoiceValidator::date);
        Optional<LocalDate> dueDate = Optional.ofNullable(values.get("due_date")).flatMap(InvoiceValidator::date);
        if (invoiceDate.isPresent() && invoiceDate.get().isAfter(today.plusDays(1))) {
            add(issues, "invoice_date", "DATE_IN_FUTURE", Severity.WARNING, "The invoice date is in the future.");
        }
        if (invoiceDate.isPresent() && dueDate.isPresent() && dueDate.get().isBefore(invoiceDate.get())) {
            add(issues, "due_date", "DUE_BEFORE_INVOICE", Severity.WARNING, "The due date is before the invoice date.");
        }
        return issues;
    }

    private static Optional<BigDecimal> amount(Map<String, String> values, String key) {
        return Optional.ofNullable(values.get(key)).flatMap(Amounts::parse);
    }

    static Optional<LocalDate> date(String v) {
        try {
            return Optional.of(LocalDate.parse(v));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static void add(Map<String, List<ValidationIssue>> issues, String key, String rule, Severity severity, String message) {
        issues.computeIfAbsent(key, k -> new ArrayList<>()).add(new ValidationIssue(rule, severity, message));
    }
}
