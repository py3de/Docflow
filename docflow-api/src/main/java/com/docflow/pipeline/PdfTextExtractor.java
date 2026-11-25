package com.docflow.pipeline;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/**
 * Stage 1: reads the text layer of a digital PDF with Apache PDFBox.
 * Scanned PDFs have no text layer and need OCR (Tesseract), which is not part of this step.
 */
@Component
public class PdfTextExtractor {

    /** Invoices are short; reading at most this many pages bounds time and LLM cost. */
    static final int MAX_PAGES = 10;

    public PageText extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PositionStripper stripper = new PositionStripper();
            stripper.setSortByPosition(true);       // reading order, not content-stream order
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(MAX_PAGES, document.getNumberOfPages()));
            String text = stripper.getText(document);
            return new PageText(text, List.copyOf(stripper.words), document.getNumberOfPages());
        } catch (IOException e) {
            throw new UnreadableDocumentException("The PDF could not be read: " + e.getMessage(), e);
        }
    }

    /** Renders one page as PNG for the review screen. pageIndex is 0-based. */
    public byte[] renderPage(byte[] pdf, int pageIndex, float dpi) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new UnreadableDocumentException("The document has no page " + (pageIndex + 1) + ".", null);
            }
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UnreadableDocumentException("The PDF could not be rendered: " + e.getMessage(), e);
        }
    }

    /** Collects each word of page 1 with its position while PDFBox writes the text. */
    private static final class PositionStripper extends PDFTextStripper {

        private final List<PageText.Word> words = new ArrayList<>();
        private int line = -1;
        private float lastBaseline = Float.NaN;

        PositionStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            super.writeString(text, positions);
            if (getCurrentPageNo() != 1 || positions.isEmpty() || text.isBlank()) {
                return;
            }
            // Words whose baselines are within 2pt of each other are on the same line.
            float baseline = positions.get(0).getYDirAdj();
            if (Float.isNaN(lastBaseline) || Math.abs(baseline - lastBaseline) > 2f) {
                line++;
                lastBaseline = baseline;
            }
            // PDFBox may hand over "VAT ID: DE287451903" as one piece: split it at real spaces,
            // so each word gets its own position.
            List<TextPosition> run = new ArrayList<>();
            for (TextPosition p : positions) {
                String ch = p.getUnicode();
                if (ch == null || ch.isBlank()) {
                    addWord(run);
                    run = new ArrayList<>();
                } else {
                    run.add(p);
                }
            }
            addWord(run);
        }

        private void addWord(List<TextPosition> run) {
            if (run.isEmpty()) {
                return;
            }
            TextPosition first = run.get(0);
            float pageWidth = first.getPageWidth();
            float pageHeight = first.getPageHeight();
            if (pageWidth <= 0 || pageHeight <= 0) {
                return;
            }
            StringBuilder text = new StringBuilder();
            float minX = Float.MAX_VALUE, maxX = 0, top = Float.MAX_VALUE, bottom = 0;
            for (TextPosition p : run) {
                text.append(p.getUnicode());
                minX = Math.min(minX, p.getXDirAdj());
                maxX = Math.max(maxX, p.getXDirAdj() + p.getWidthDirAdj());
                top = Math.min(top, p.getYDirAdj() - p.getHeightDir());
                bottom = Math.max(bottom, p.getYDirAdj());
            }
            words.add(new PageText.Word(text.toString(), minX / pageWidth, top / pageHeight,
                    (maxX - minX) / pageWidth, Math.max(bottom - top, 1f) / pageHeight, line));
        }
    }
}
