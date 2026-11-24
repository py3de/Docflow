-- DocFlow schema, version 1.
-- Written to run on both PostgreSQL and H2 (PostgreSQL mode).

CREATE TABLE document (
    id                VARCHAR(40)    PRIMARY KEY,
    tenant_id         VARCHAR(40)    NOT NULL,
    file_name         VARCHAR(255)   NOT NULL,
    doc_type          VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    received_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    vendor            VARCHAR(200)   NOT NULL,
    gross_amount      NUMERIC(14, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    avg_confidence    DOUBLE PRECISION NOT NULL,
    model             VARCHAR(80)    NOT NULL,
    tokens            INTEGER        NOT NULL,
    cost_eur          NUMERIC(12, 6) NOT NULL,
    total_latency_ms  INTEGER        NOT NULL,
    review_required   BOOLEAN        NOT NULL,
    trace_id          VARCHAR(64)    NOT NULL,
    sha256            VARCHAR(64),
    storage_path      VARCHAR(500),
    po_number         VARCHAR(60),
    po_vendor         VARCHAR(200),
    po_amount         NUMERIC(14, 2),
    po_score          DOUBLE PRECISION,
    version           BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_document_tenant_received ON document (tenant_id, received_at DESC);
CREATE INDEX ix_document_tenant_status   ON document (tenant_id, status);
-- Same file uploaded twice by the same tenant = same document (idempotent upload)
CREATE UNIQUE INDEX ux_document_tenant_sha ON document (tenant_id, sha256);

CREATE TABLE document_line_item (
    document_id  VARCHAR(40)    NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    item_order   INTEGER        NOT NULL,
    description  VARCHAR(300)   NOT NULL,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(14, 2) NOT NULL,
    total        NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (document_id, item_order)
);

CREATE TABLE document_stage_timing (
    document_id  VARCHAR(40) NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    item_order   INTEGER     NOT NULL,
    stage        VARCHAR(30) NOT NULL,
    ms           INTEGER     NOT NULL,
    PRIMARY KEY (document_id, item_order)
);

CREATE TABLE document_po_delta (
    document_id  VARCHAR(40)  NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    item_order   INTEGER      NOT NULL,
    delta        VARCHAR(300) NOT NULL,
    PRIMARY KEY (document_id, item_order)
);

CREATE TABLE extracted_field (
    id           VARCHAR(40)  PRIMARY KEY,
    document_id  VARCHAR(40)  NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    field_order  INTEGER      NOT NULL,
    field_key    VARCHAR(60)  NOT NULL,
    label        VARCHAR(100) NOT NULL,
    field_value  VARCHAR(500) NOT NULL,
    source_text  VARCHAR(500),
    confidence   DOUBLE PRECISION NOT NULL,
    box_x        DOUBLE PRECISION,
    box_y        DOUBLE PRECISION,
    box_w        DOUBLE PRECISION,
    box_h        DOUBLE PRECISION,
    corrected    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ux_field_doc_key UNIQUE (document_id, field_key)
);

CREATE TABLE field_issue (
    field_id    VARCHAR(40)  NOT NULL REFERENCES extracted_field (id) ON DELETE CASCADE,
    item_order  INTEGER      NOT NULL,
    rule        VARCHAR(60)  NOT NULL,
    severity    VARCHAR(20)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    PRIMARY KEY (field_id, item_order)
);

-- Every human correction is kept: this is the feedback loop into the evaluation dataset.
CREATE TABLE field_correction (
    id            VARCHAR(40)  PRIMARY KEY,
    document_id   VARCHAR(40)  NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    tenant_id     VARCHAR(40)  NOT NULL,
    field_key     VARCHAR(60)  NOT NULL,
    old_value     VARCHAR(500) NOT NULL,
    new_value     VARCHAR(500) NOT NULL,
    corrected_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_correction_document ON field_correction (document_id);

-- Remembers Idempotency-Key headers so a retried request is not applied twice.
CREATE TABLE idempotency_record (
    idempotency_key  VARCHAR(100) PRIMARY KEY,
    tenant_id        VARCHAR(40)  NOT NULL,
    document_id      VARCHAR(40)  NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE eval_run (
    id          VARCHAR(40)  PRIMARY KEY,
    commit_sha  VARCHAR(40)  NOT NULL,
    branch      VARCHAR(120) NOT NULL,
    model       VARCHAR(80)  NOT NULL,
    dataset     VARCHAR(200) NOT NULL,
    started_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    f1          DOUBLE PRECISION NOT NULL,
    threshold   DOUBLE PRECISION NOT NULL,
    passed      BOOLEAN      NOT NULL
);
CREATE INDEX ix_eval_started ON eval_run (started_at DESC);

CREATE TABLE eval_field_score (
    eval_run_id  VARCHAR(40)  NOT NULL REFERENCES eval_run (id) ON DELETE CASCADE,
    item_order   INTEGER      NOT NULL,
    field        VARCHAR(100) NOT NULL,
    f1           DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (eval_run_id, item_order)
);
