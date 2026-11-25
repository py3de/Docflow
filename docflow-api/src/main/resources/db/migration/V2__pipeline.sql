-- Step 2: extraction pipeline.

ALTER TABLE document ADD COLUMN failure_reason VARCHAR(500);
ALTER TABLE document ADD COLUMN page_count INTEGER;

-- Purchase orders the invoices are matched against (normally synced from the ERP).
CREATE TABLE purchase_order (
    id          VARCHAR(40)    PRIMARY KEY,
    tenant_id   VARCHAR(40)    NOT NULL,
    po_number   VARCHAR(60)    NOT NULL,
    vendor      VARCHAR(200)   NOT NULL,
    amount      NUMERIC(14, 2) NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    CONSTRAINT ux_po_tenant_number UNIQUE (tenant_id, po_number)
);
