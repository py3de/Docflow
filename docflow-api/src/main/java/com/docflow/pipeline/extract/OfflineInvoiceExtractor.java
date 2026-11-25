package com.docflow.pipeline.extract;

import com.docflow.pipeline.Amounts;
import com.docflow.pipeline.extract.ExtractedInvoice.FieldGuess;
import com.docflow.pipeline.extract.ExtractedInvoice.LineGuess;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based extractor: finds fields by their printed labels ("Invoice no.:", "Total due:" ...).
 * No API key, no cost, deterministic, so tests and demos never depend on an external service.
 * It handles cleanly laid-out invoices like the samples; real-world layouts need an LLM provider.
 */
public class OfflineInvoiceExtractor implements InvoiceExtractor {

    static final String MODEL = "offline-rules";
    private static final double LABEL_CONFIDENCE = 0.92;
    private static final double GUESS_CONFIDENCE = 0.88;

    private static final String AMOUNT = "([\\d.,]+\\d)";
    private static final String DATE = "(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\.\\d{1,2}\\.\\d{4})";

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put("vendor_vat_id", Pattern.compile(
                "(?i)(?:VAT ID|VAT No\\.?|USt-IdNr\\.?)\\s*:?\\s*([A-Z]{2}[A-Z0-9]{8,12})\\b"));
        PATTERNS.put("invoice_number", Pattern.compile(
                "(?i)(?:Invoice no\\.?|Invoice number|Rechnungsnummer|Rechnung Nr\\.?)\\s*:?\\s*([A-Z0-9][A-Z0-9/-]{3,})"));
        PATTERNS.put("invoice_date", Pattern.compile(
                "(?im)^(?:Invoice date|Date|Rechnungsdatum|Datum)\\s*:\\s*" + DATE));
        PATTERNS.put("po_number", Pattern.compile(
                "(?i)(?:Your order|PO number|Purchase order|Bestellnummer|Order no\\.?)\\s*:?\\s*([A-Z0-9][A-Z0-9-]{3,})"));
        PATTERNS.put("net_amount", Pattern.compile(
                "(?im)^(?:Net amount|Net|Subtotal|Nettobetrag)\\s*:?\\s*" + AMOUNT));
        PATTERNS.put("vat_amount", Pattern.compile(
                "(?im)^(?:VAT|MwSt\\.?|USt\\.?)\\b[^:\\n]*:\\s*" + AMOUNT));
        PATTERNS.put("gross_amount", Pattern.compile(
                "(?im)^(?:Total due|Gross amount|Amount due|Total|Gesamtbetrag|Rechnungsbetrag)\\s*:?\\s*" + AMOUNT));
        PATTERNS.put("due_date", Pattern.compile(
                "(?i)(?:Payable by|Due date|Zahlbar bis)\\s*:?\\s*" + DATE));
    }

    private static final Pattern LINE_ITEM = Pattern.compile(
            "(?m)^(.+?)\\s+(\\d+(?:[.,]\\d+)?)\\s+(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})\\s+(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})\\s*$");
    private static final Pattern GERMAN_DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

    @Override
    public ExtractedInvoice extract(String text) {
        Map<String, FieldGuess> fields = new LinkedHashMap<>();
        fields.put("vendor_name", firstLine(text));
        for (Map.Entry<String, Pattern> e : PATTERNS.entrySet()) {
            Matcher m = e.getValue().matcher(text);
            String value = m.find() ? m.group(1).trim() : null;
            if (value != null && e.getKey().endsWith("_amount")) {
                value = Amounts.parse(value).map(Amounts::canonical).orElse(value);
            }
            if (value != null && e.getKey().endsWith("_date")) {
                value = isoDate(value);
            }
            fields.put(e.getKey(), new FieldGuess(value, value == null ? 0 : LABEL_CONFIDENCE));
        }

        List<LineGuess> lines = new ArrayList<>();
        Matcher m = LINE_ITEM.matcher(text);
        while (m.find()) {
            lines.add(new LineGuess(m.group(1).trim(), new BigDecimal(m.group(2).replace(',', '.')),
                    Amounts.parse(m.group(3)).orElse(null), Amounts.parse(m.group(4)).orElse(null)));
        }
        String currency = text.contains("EUR") || text.contains("€") ? "EUR" : null;
        return new ExtractedInvoice(fields, currency, lines, MODEL, 0, 0);
    }

    @Override
    public String describe() {
        return MODEL;
    }

    private static FieldGuess firstLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return new FieldGuess(line.trim(), GUESS_CONFIDENCE);
            }
        }
        return new FieldGuess(null, 0);
    }

    private static String isoDate(String value) {
        Matcher m = GERMAN_DATE.matcher(value);
        if (m.matches()) {
            return "%s-%02d-%02d".formatted(m.group(3), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        return value;
    }
}
