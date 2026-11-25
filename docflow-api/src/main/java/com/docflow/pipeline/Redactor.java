package com.docflow.pipeline;

import java.util.regex.Pattern;

/**
 * Removes personal data that is not needed for extraction before text is sent to an external LLM:
 * bank account numbers (IBAN), e-mail addresses and phone numbers.
 * VAT IDs, amounts and names of companies are kept: the extraction needs them.
 */
public final class Redactor {

    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]{4}){2,7}(?:[ ]?[A-Z0-9]{1,3})?\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    /** International numbers (+49 ...) or numbers after a label; plain dates like 2026-11-20 stay. */
    private static final Pattern PHONE = Pattern.compile(
            "\\+\\d{1,3}[ \\d/()-]{6,}\\d|(?i)(?:tel|phone|fax|telefon)\\.?:?\\s*[\\d +/()-]{6,}\\d");

    private Redactor() {
    }

    public static String redact(String text) {
        if (text == null) {
            return "";
        }
        String out = IBAN.matcher(text).replaceAll("[IBAN]");
        out = EMAIL.matcher(out).replaceAll("[EMAIL]");
        return PHONE.matcher(out).replaceAll("[PHONE]");
    }
}
