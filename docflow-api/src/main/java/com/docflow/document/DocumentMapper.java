package com.docflow.document;

import com.docflow.document.DocumentDtos.Box;
import com.docflow.document.DocumentDtos.DocumentJob;
import com.docflow.document.DocumentDtos.DocumentSummary;
import com.docflow.document.DocumentDtos.Field;
import com.docflow.document.DocumentDtos.Issue;
import com.docflow.document.DocumentDtos.Line;
import com.docflow.document.DocumentDtos.PoMatch;
import com.docflow.document.DocumentDtos.Timing;
import java.util.List;

/** Entity → API record. Must run inside a transaction because it reads lazy collections. */
final class DocumentMapper {

    private DocumentMapper() {
    }

    static DocumentSummary toSummary(DocumentEntity d) {
        return new DocumentSummary(d.getId(), d.getTenantId(), d.getFileName(), d.getDocType(), d.getStatus(),
                d.getReceivedAt(), d.getVendor(), d.getGrossAmount(), d.getCurrency(), d.getAvgConfidence(),
                d.getModel(), d.getTokens(), d.getCostEur());
    }

    static DocumentJob toJob(DocumentEntity d) {
        List<Timing> timings = d.getTimings().stream().map(t -> new Timing(t.getStage(), t.getMs())).toList();
        List<Field> fields = d.getFields().stream().map(DocumentMapper::toField).toList();
        List<Line> lines = d.getLineItems().stream()
                .map(l -> new Line(l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getTotal()))
                .toList();
        PoMatch po = d.getPoNumber() == null ? null
                : new PoMatch(d.getPoNumber(), d.getPoVendor(), d.getPoAmount(),
                d.getPoScore() == null ? 0 : d.getPoScore(), List.copyOf(d.getPoDeltas()));
        return new DocumentJob(d.getId(), d.getTenantId(), d.getFileName(), d.getDocType(), d.getStatus(),
                d.getReceivedAt(), d.getVendor(), d.getGrossAmount(), d.getCurrency(), d.getAvgConfidence(),
                d.getModel(), d.getTokens(), d.getCostEur(), timings, fields, lines, po, d.getTraceId());
    }

    private static Field toField(ExtractedFieldEntity f) {
        BoundingBox b = f.getBox();
        Box box = (b == null || b.isEmpty()) ? null : new Box(b.getX(), b.getY(), b.getW(), b.getH());
        List<Issue> issues = f.getIssues().stream()
                .map(i -> new Issue(i.getRule(), i.getSeverity(), i.getMessage()))
                .toList();
        return new Field(f.getKey(), f.getLabel(), f.getValue(), f.getSourceText(), f.getConfidence(),
                box, issues, f.isCorrected());
    }
}
