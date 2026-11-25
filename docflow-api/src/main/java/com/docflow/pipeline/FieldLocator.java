package com.docflow.pipeline;

import com.docflow.document.BoundingBox;
import com.docflow.pipeline.FieldCatalog.FieldDef;
import com.docflow.pipeline.PageText.Word;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds an extracted value again among the words of page 1, so the review screen can highlight it.
 * A value the model returned but that is not on the page is a warning sign: the pipeline lowers its confidence.
 */
public final class FieldLocator {

    /** Where the value was found, and the text as it is printed there. */
    public record Location(BoundingBox box, String pageText) {
    }

    /** Words on the same line as these hints make an occurrence the right one, e.g. "Total" for the gross amount. */
    private static final java.util.Map<String, Pattern> LINE_HINTS = java.util.Map.of(
            "net_amount", Pattern.compile("(?i)net|subtotal|netto"),
            "vat_amount", Pattern.compile("(?i)vat|mwst|ust|tax"),
            "gross_amount", Pattern.compile("(?i)total|gross|due|brutto|gesamt"),
            "invoice_date", Pattern.compile("(?i)^(?!.*(?:payable|due|zahlbar)).*(?:date|datum)"),
            "due_date", Pattern.compile("(?i)payable|due|zahlbar"));

    private static final Pattern GERMAN_DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

    private FieldLocator() {
    }

    public static Optional<Location> locate(FieldDef def, String value, PageText page) {
        if (value == null || value.isBlank() || page.firstPageWords().isEmpty()) {
            return Optional.empty();
        }
        List<List<Word>> candidates = switch (def.kind()) {
            case AMOUNT -> singleWords(page, w -> Amounts.parse(w.text()).map(a -> sameAmount(a, value)).orElse(false));
            case DATE -> singleWords(page, w -> value.equals(isoDate(clean(w.text()))));
            case TEXT -> sequences(page, value);
        };
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<Word> best = pick(def.key(), candidates, page);
        String text = best.stream().map(Word::text).collect(Collectors.joining(" "));
        return Optional.of(new Location(union(best), def.kind() == FieldCatalog.Kind.TEXT ? clean(text) : value));
    }

    // ---- matching -----------------------------------------------------------------

    private static List<List<Word>> singleWords(PageText page, java.util.function.Predicate<Word> test) {
        List<List<Word>> out = new ArrayList<>();
        for (Word w : page.firstPageWords()) {
            if (test.test(w)) {
                out.add(List.of(w));
            }
        }
        return out;
    }

    /** Consecutive words on one line whose text, joined, equals the value (case and punctuation ignored). */
    private static List<List<Word>> sequences(PageText page, String value) {
        String target = norm(value);
        List<Word> words = page.firstPageWords();
        List<List<Word>> out = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            StringBuilder joined = new StringBuilder();
            for (int j = i; j < words.size() && words.get(j).line() == words.get(i).line(); j++) {
                joined.append(norm(words.get(j).text()));
                String sofar = joined.toString();
                if (sofar.equals(target)) {
                    out.add(words.subList(i, j + 1));
                    break;
                }
                if (!target.startsWith(sofar)) {
                    break;
                }
            }
        }
        return out;
    }

    /** Prefers an occurrence on a line with the field's label; otherwise the last one (totals sit at the end). */
    private static List<Word> pick(String key, List<List<Word>> candidates, PageText page) {
        Pattern hint = LINE_HINTS.get(key);
        if (hint != null) {
            for (List<Word> c : candidates) {
                int line = c.get(0).line();
                String lineText = page.firstPageWords().stream().filter(w -> w.line() == line)
                        .map(Word::text).collect(Collectors.joining(" "));
                if (hint.matcher(lineText).find()) {
                    return c;
                }
            }
            return candidates.get(candidates.size() - 1);
        }
        return candidates.get(0);
    }

    private static BoundingBox union(List<Word> words) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = 0, y1 = 0;
        for (Word w : words) {
            x0 = Math.min(x0, w.x());
            y0 = Math.min(y0, w.y());
            x1 = Math.max(x1, w.x() + w.w());
            y1 = Math.max(y1, w.y() + w.h());
        }
        double padX = 0.004, padY = 0.003;
        return new BoundingBox(Math.max(0, x0 - padX), Math.max(0, y0 - padY),
                Math.min(1, x1 - x0 + 2 * padX), Math.min(1, y1 - y0 + 2 * padY));
    }

    // ---- normalising --------------------------------------------------------------

    private static boolean sameAmount(BigDecimal amount, String value) {
        return Amounts.parse(value).map(v -> v.compareTo(amount) == 0).orElse(false);
    }

    private static String clean(String s) {
        return s.replaceAll("^[\\p{Punct}&&[^(]]+|[\\p{Punct}&&[^)]]+$", "").trim();
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static String isoDate(String s) {
        Matcher m = GERMAN_DATE.matcher(s);
        if (m.matches()) {
            return "%s-%02d-%02d".formatted(m.group(3), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        return s;
    }
}
