package com.docflow.demo;

import com.docflow.config.DocFlowProperties;
import com.docflow.document.BoundingBox;
import com.docflow.document.DocStatus;
import com.docflow.document.DocType;
import com.docflow.document.DocumentEntity;
import com.docflow.document.DocumentRepository;
import com.docflow.document.ExtractedFieldEntity;
import com.docflow.document.LineItem;
import com.docflow.document.PipelineStage;
import com.docflow.document.Severity;
import com.docflow.document.StageTiming;
import com.docflow.document.ValidationIssue;
import com.docflow.eval.EvalRunEntity;
import com.docflow.eval.EvalRunRepository;
import com.docflow.eval.FieldScore;
import com.docflow.pipeline.po.PurchaseOrder;
import com.docflow.pipeline.po.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills an empty database with realistic, fictional invoices so the dashboard has data on first start.
 * Deterministic (fixed seed) and relative to "now", so the charts always show the last 14 days.
 * Turn off with docflow.demo-data.enabled=false.
 */
@Component
@ConditionalOnProperty(prefix = "docflow.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int BATCH = 300;

    private record Vendor(String name, String vatId) {
    }

    private record Item(String description, String price) {
    }

    private record Layout(String label, BoundingBox box) {
    }

    // Fictional vendors only.
    private static final List<Vendor> VENDORS = List.of(
            new Vendor("Nordwerk Logistik GmbH", "DE287451903"),
            new Vendor("Alpenblick Papier AG", "DE311902846"),
            new Vendor("Rheintal Office Supply", "DE194477310"),
            new Vendor("Kessler Maschinenbau KG", "DE265018837"),
            new Vendor("Brightline Cloud Services", "DE330571264"),
            new Vendor("Schwarzwald Druck GmbH", "DE148830952"),
            new Vendor("Mainfeld Facility Services", "DE276604418"));

    private static final List<Item> ITEMS = List.of(
            new Item("Pallet transport Walldorf–Mannheim", "180.00"),
            new Item("Copy paper A4, 80 g (box of 5)", "24.90"),
            new Item("Toner cartridge, black", "89.00"),
            new Item("Hydraulic seal kit", "46.50"),
            new Item("Cloud storage, 1 TB / month", "19.99"),
            new Item("Brochure print run, 500 pcs", "312.00"),
            new Item("Office cleaning, monthly", "640.00"),
            new Item("Forklift maintenance", "275.00"),
            new Item("Shipping labels (roll of 1,000)", "32.40"));

    private static final List<String> MODELS = List.of("gpt-4.1-mini", "claude-haiku-4-5", "mistral-small");

    /** Where each field sits on the rendered invoice page. Must match the UI's FIELD_LAYOUT. */
    private static final Map<String, Layout> LAYOUT = new LinkedHashMap<>();

    static {
        LAYOUT.put("vendor_name", new Layout("Vendor name", new BoundingBox(0.07, 0.06, 0.46, 0.045)));
        LAYOUT.put("vendor_vat_id", new Layout("Vendor VAT ID", new BoundingBox(0.07, 0.115, 0.3, 0.03)));
        LAYOUT.put("invoice_number", new Layout("Invoice number", new BoundingBox(0.58, 0.2, 0.35, 0.03)));
        LAYOUT.put("invoice_date", new Layout("Invoice date", new BoundingBox(0.58, 0.235, 0.35, 0.03)));
        LAYOUT.put("po_number", new Layout("PO number", new BoundingBox(0.58, 0.27, 0.35, 0.03)));
        LAYOUT.put("net_amount", new Layout("Net amount", new BoundingBox(0.58, 0.7, 0.35, 0.03)));
        LAYOUT.put("vat_amount", new Layout("VAT (19%)", new BoundingBox(0.58, 0.735, 0.35, 0.03)));
        LAYOUT.put("gross_amount", new Layout("Gross amount", new BoundingBox(0.58, 0.775, 0.35, 0.035)));
        LAYOUT.put("due_date", new Layout("Due date", new BoundingBox(0.07, 0.86, 0.4, 0.03)));
    }

    private final DocumentRepository documents;
    private final EvalRunRepository evalRuns;
    private final PurchaseOrderRepository purchaseOrders;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int days;
    private final Random rnd = new Random(42);
    private int reviewCounter;
    private int invoiceCounter;

    public DemoDataSeeder(DocumentRepository documents, EvalRunRepository evalRuns,
                          PurchaseOrderRepository purchaseOrders, TransactionTemplate tx,
                          Clock clock, DocFlowProperties properties) {
        this.documents = documents;
        this.evalRuns = evalRuns;
        this.purchaseOrders = purchaseOrders;
        this.tx = tx;
        this.clock = clock;
        this.days = Math.max(1, properties.demoData().days());
    }

    @Override
    public void run(ApplicationArguments args) {
        if (documents.count() > 0) {
            log.info("Demo data: database already has documents, skipping.");
            return;
        }
        long started = System.currentTimeMillis();
        Instant now = clock.instant();
        List<DocumentEntity> batch = new ArrayList<>(BATCH);
        int total = 0;
        for (String tenant : List.of("acme-de", "globex-eu")) {
            for (DocumentEntity doc : generateTenant(tenant, now)) {
                batch.add(doc);
                if (batch.size() == BATCH) {
                    total += saveBatch(batch);
                }
            }
        }
        total += saveBatch(batch);
        tx.executeWithoutResult(s -> evalRuns.saveAll(generateEvalRuns(now)));
        tx.executeWithoutResult(s -> purchaseOrders.saveAll(purchaseOrders()));
        log.info("Demo data: seeded {} documents in {} ms.", total, System.currentTimeMillis() - started);
    }

    private int saveBatch(List<DocumentEntity> batch) {
        int n = batch.size();
        if (n > 0) {
            List<DocumentEntity> copy = List.copyOf(batch);
            tx.executeWithoutResult(s -> documents.saveAll(copy));
            batch.clear();
        }
        return n;
    }

    // ---- documents ------------------------------------------------------------

    private List<DocumentEntity> generateTenant(String tenant, Instant now) {
        boolean big = tenant.equals("acme-de");
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<DocumentEntity> result = new ArrayList<>();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            int perDay = weekend ? between(big ? 15 : 5, big ? 30 : 12) : between(big ? 120 : 35, big ? 170 : 60);
            Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
            long windowSeconds = offset == 0 ? Duration.between(start, now).getSeconds() : 86_400;
            if (offset == 0) {
                perDay = (int) Math.round(perDay * windowSeconds / 86_400.0);
            }
            for (int i = 0; i < perDay; i++) {
                Instant received = start.plusSeconds((long) (rnd.nextDouble() * Math.max(1, windowSeconds)));
                result.add(document(tenant, received, now));
            }
        }
        return result;
    }

    private DocumentEntity document(String tenant, Instant received, Instant now) {
        Vendor vendor = VENDORS.get(rnd.nextInt(VENDORS.size()));
        Duration age = Duration.between(received, now);
        double roll = rnd.nextDouble();

        DocStatus status;
        if (age.toMinutes() < 3) {
            status = rnd.nextBoolean() ? DocStatus.EXTRACTING : DocStatus.VALIDATING;
        } else if (roll < 0.01) {
            status = DocStatus.FAILED;
        } else if (roll < 0.14) {
            status = DocStatus.NEEDS_REVIEW;   // older ones get approved below
        } else {
            status = DocStatus.MATCHED;
        }

        invoiceCounter++;
        String invoiceNo = "INV-2026-%06d".formatted(10_400 + invoiceCounter);
        String poNo = "PO-4500%05d".formatted(81_200 + invoiceCounter);
        DocType type = rnd.nextDouble() > 0.9 ? DocType.CREDIT_NOTE : DocType.INVOICE;
        String fileName = vendor.name().split(" ")[0].toLowerCase() + "_" + invoiceNo.toLowerCase() + ".pdf";
        DocumentEntity doc = new DocumentEntity("doc_%012x".formatted(rnd.nextLong() & 0xFFFF_FFFF_FFFFL),
                tenant, fileName, type, received);

        // Line items and totals
        int lines = 1 + rnd.nextInt(4);
        BigDecimal net = BigDecimal.ZERO;
        for (int i = 0; i < lines; i++) {
            Item item = ITEMS.get(rnd.nextInt(ITEMS.size()));
            int qty = 1 + rnd.nextInt(6);
            BigDecimal price = new BigDecimal(item.price());
            BigDecimal total = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            doc.addLineItem(new LineItem(item.description(), qty, price, total));
            net = net.add(total);
        }
        BigDecimal vat = net.multiply(new BigDecimal("0.19")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gross = net.add(vat);

        // Pipeline timings: in-flight and failed documents stop early
        int stages = switch (status) {
            case EXTRACTING, FAILED -> 2;
            case VALIDATING -> 3;
            default -> 5;
        };
        int[][] ranges = {{40, 120}, {300, 1400}, {1200, 3800}, {20, 90}, {60, 260}};
        PipelineStage[] order = PipelineStage.values();
        for (int i = 0; i < stages; i++) {
            doc.addTiming(new StageTiming(order[i], between(ranges[i][0], ranges[i][1])));
        }

        boolean review = status == DocStatus.NEEDS_REVIEW;
        // Two kinds of review case, alternating: wrong totals + OCR'd PO number, or date/VAT-ID format issues.
        boolean totalsCase = review && reviewCounter++ % 2 == 0;
        boolean extracted = status != DocStatus.FAILED && status != DocStatus.EXTRACTING;
        if (extracted) {
            addFields(doc, vendor, invoiceNo, poNo, received, net, vat, gross, review, totalsCase);
        }

        int tokens = between(1800, 5200);
        BigDecimal cost = BigDecimal.valueOf(tokens * 0.0000009 + 0.0012).setScale(6, RoundingMode.HALF_UP);
        doc.recordExtraction(vendor.name(), gross, "EUR", MODELS.get(rnd.nextInt(MODELS.size())), tokens, cost,
                new UUID(rnd.nextLong(), rnd.nextLong()).toString().replace("-", ""));

        if (status == DocStatus.MATCHED || review) {
            boolean amountOff = totalsCase;
            doc.recordPoMatch(poNo, vendor.name(), amountOff ? gross.subtract(BigDecimal.TEN) : gross,
                    review ? round2(0.71 + rnd.nextDouble() * 0.13) : round2(0.93 + rnd.nextDouble() * 0.065),
                    amountOff ? List.of("Gross amount differs from PO by 10.00 EUR") : List.of());
        }

        doc.changeStatus(status);
        if (status == DocStatus.NEEDS_REVIEW && age.toHours() >= 24) {
            // Older review cases were already handled by a reviewer.
            doc.getFields().forEach(f -> f.applyReview(f.getSourceText()));
            doc.changeStatus(DocStatus.APPROVED);
        }
        return doc;
    }

    private void addFields(DocumentEntity doc, Vendor vendor, String invoiceNo, String poNo, Instant received,
                           BigDecimal net, BigDecimal vat, BigDecimal gross, boolean lowConfidence,
                           boolean totalsCase) {
        LocalDate date = LocalDate.ofInstant(received, ZoneOffset.UTC);
        Map<String, String> page = new LinkedHashMap<>();
        page.put("vendor_name", vendor.name());
        page.put("vendor_vat_id", vendor.vatId());
        page.put("invoice_number", invoiceNo);
        page.put("invoice_date", date.toString());
        page.put("po_number", poNo);
        page.put("net_amount", net.toPlainString());
        page.put("vat_amount", vat.toPlainString());
        page.put("gross_amount", gross.toPlainString());
        page.put("due_date", date.plusDays(30).toString());

        List<String> weak = !lowConfidence ? List.of()
                : totalsCase ? List.of("po_number", "vat_amount") : List.of("invoice_date", "vendor_vat_id");

        for (Map.Entry<String, Layout> e : LAYOUT.entrySet()) {
            String key = e.getKey();
            String onPage = page.get(key);
            String value = onPage;
            List<ValidationIssue> issues = new ArrayList<>();
            boolean isWeak = weak.contains(key);
            if (isWeak) {
                switch (key) {
                    case "vat_amount" -> {
                        value = vat.add(BigDecimal.TEN).toPlainString();
                        issues.add(new ValidationIssue("TOTALS_CONSISTENT", Severity.CRITICAL,
                                "Net + VAT does not equal gross (off by 10.00)."));
                    }
                    case "po_number" -> {
                        value = onPage.replace("4500", "45O0");
                        issues.add(new ValidationIssue("PO_FORMAT", Severity.WARNING,
                                "Letter \"O\" found where a digit is expected — likely OCR confusion."));
                    }
                    case "invoice_date" -> {
                        value = "%02d.%02d.%d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
                        issues.add(new ValidationIssue("DATE_ISO", Severity.WARNING,
                                "Date returned as DD.MM.YYYY; normalised value needs confirmation."));
                    }
                    case "vendor_vat_id" -> {
                        value = onPage.substring(0, onPage.length() - 1);
                        issues.add(new ValidationIssue("VAT_ID_FORMAT", Severity.CRITICAL,
                                "German VAT IDs have 9 digits after \"DE\"; found 8."));
                    }
                    default -> { }
                }
            }
            double base = isWeak ? 0.58 : (lowConfidence ? 0.90 : 0.955);
            double confidence = round2(Math.min(0.995, Math.max(0.31, base + (rnd.nextDouble() - 0.5) * 0.08)));
            ExtractedFieldEntity field = new ExtractedFieldEntity(new UUID(rnd.nextLong(), rnd.nextLong()).toString(),
                    key, e.getValue().label(), value, onPage, confidence, e.getValue().box());
            issues.forEach(field::addIssue);
            doc.addField(field);
        }
    }

    // ---- purchase orders --------------------------------------------------------

    /**
     * Open purchase orders, including the ones the sample invoices in /samples refer to:
     * sample-invoice-clean.pdf      → PO-450099001 matches exactly            → MATCHED automatically
     * sample-invoice-vat-error.pdf  → PO-450099002, but the VAT is wrong      → NEEDS_REVIEW
     * sample-invoice-wrong-po.pdf   → prints PO-450099999; fuzzy match finds PO-450099003 → NEEDS_REVIEW
     */
    private static List<PurchaseOrder> purchaseOrders() {
        List<PurchaseOrder> list = new ArrayList<>();
        list.add(new PurchaseOrder("po_demo_001", "acme-de", "PO-450099001", "Nordwerk Logistik GmbH", new BigDecimal("719.71"), "EUR"));
        list.add(new PurchaseOrder("po_demo_002", "acme-de", "PO-450099002", "Alpenblick Papier AG", new BigDecimal("508.13"), "EUR"));
        list.add(new PurchaseOrder("po_demo_003", "acme-de", "PO-450099003", "Rheintal Office Supply", new BigDecimal("761.60"), "EUR"));
        String[][] more = {
                {"PO-450077110", "Kessler Maschinenbau KG", "1650.00"},
                {"PO-450077111", "Brightline Cloud Services", "237.88"},
                {"PO-450077112", "Schwarzwald Druck GmbH", "1856.40"},
                {"PO-450077113", "Mainfeld Facility Services", "761.60"},
        };
        for (int i = 0; i < more.length; i++) {
            list.add(new PurchaseOrder("po_demo_1" + i, "acme-de", more[i][0], more[i][1], new BigDecimal(more[i][2]), "EUR"));
            list.add(new PurchaseOrder("po_demo_2" + i, "globex-eu", more[i][0], more[i][1], new BigDecimal(more[i][2]), "EUR"));
        }
        return list;
    }

    // ---- evaluation runs --------------------------------------------------------

    private List<EvalRunEntity> generateEvalRuns(Instant now) {
        List<String> labels = LAYOUT.values().stream().map(Layout::label).toList();
        double[] fieldOffsets = {0.05, -0.06, 0.03, 0.01, -0.09, 0.02, -0.04, 0.04, 0.0};
        List<String> branches = List.of("feat/ocr-tuning", "feat/schema-v2", "fix/vat-rule", "main");
        List<EvalRunEntity> result = new ArrayList<>();
        for (int k = 0; k < 16; k++) {
            double f1 = k == 11 ? 0.874   // a regression the gate caught
                    : Math.round(Math.min(0.962, 0.86 + k * 0.0055 + (rnd.nextDouble() - 0.5) * 0.016) * 1000) / 1000.0;
            List<FieldScore> scores = new ArrayList<>();
            for (int j = 0; j < labels.size(); j++) {
                double v = Math.min(0.995, f1 + fieldOffsets[j] + (rnd.nextDouble() - 0.5) * 0.02);
                scores.add(new FieldScore(labels.get(j), round2(v)));
            }
            String branch = k == 11 ? "feat/prompt-v4" : k % 3 == 0 ? "main" : branches.get(rnd.nextInt(branches.size()));
            result.add(new EvalRunEntity("eval_" + (1200 + k), "%07x".formatted(rnd.nextInt(0x10000000)), branch,
                    k < 6 ? "gpt-4.1-mini" : "claude-haiku-4-5", "SROIE + CORD (golden, n=420)",
                    now.minus(Duration.ofMinutes((long) ((15 - k) * 1.6 * 24 * 60))), f1, 0.88, scores));
        }
        return result;
    }

    // ---- helpers ----------------------------------------------------------------

    private int between(int min, int max) {
        return min + rnd.nextInt(max - min + 1);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
