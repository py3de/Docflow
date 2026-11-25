-- Uploaded originals stored in the database (cloud profile): a Cloud Foundry app's own disk is wiped on restart.
CREATE TABLE stored_file (
    storage_path  VARCHAR(300) PRIMARY KEY,
    tenant_id     VARCHAR(40)  NOT NULL,
    content       BYTEA        NOT NULL,
    size_bytes    INTEGER      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
