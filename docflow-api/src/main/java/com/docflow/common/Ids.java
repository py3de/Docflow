package com.docflow.common;

import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    public static String documentId() {
        return "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
