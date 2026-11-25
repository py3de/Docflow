package com.docflow.pipeline;

import java.util.List;
import java.util.Set;

/** The invoice fields DocFlow extracts, in display order. Keys match the UI and the demo data. */
public final class FieldCatalog {

    public record FieldDef(String key, String label, Kind kind, boolean required) {
    }

    public enum Kind { TEXT, DATE, AMOUNT }

    public static final List<FieldDef> FIELDS = List.of(
            new FieldDef("vendor_name", "Vendor name", Kind.TEXT, true),
            new FieldDef("vendor_vat_id", "Vendor VAT ID", Kind.TEXT, false),
            new FieldDef("invoice_number", "Invoice number", Kind.TEXT, true),
            new FieldDef("invoice_date", "Invoice date", Kind.DATE, true),
            new FieldDef("po_number", "PO number", Kind.TEXT, false),
            new FieldDef("net_amount", "Net amount", Kind.AMOUNT, false),
            new FieldDef("vat_amount", "VAT (19%)", Kind.AMOUNT, false),
            new FieldDef("gross_amount", "Gross amount", Kind.AMOUNT, true),
            new FieldDef("due_date", "Due date", Kind.DATE, false));

    public static final Set<String> KEYS = Set.of(FIELDS.stream().map(FieldDef::key).toArray(String[]::new));

    private FieldCatalog() {
    }
}
