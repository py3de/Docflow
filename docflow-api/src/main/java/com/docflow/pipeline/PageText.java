package com.docflow.pipeline;

import java.util.List;

/**
 * What the text-extraction stage produces: the document's text, plus every word of the first page
 * with its position, so extracted values can be found again on the page (for the review screen's boxes).
 */
public record PageText(String text, List<Word> firstPageWords, int pageCount) {

    /** A word on the page. Coordinates are relative to the page (0..1), origin top-left. */
    public record Word(String text, double x, double y, double w, double h, int line) {
    }

    public boolean isEmpty() {
        return text == null || text.isBlank();
    }
}
