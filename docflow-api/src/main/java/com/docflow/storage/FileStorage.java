package com.docflow.storage;

/** Where uploaded files live. Local disk now; S3 / MinIO later without touching the service. */
public interface FileStorage {

    /** Stores the bytes and returns a storage path that can be saved on the document. */
    String store(String tenantId, String sha256, String extension, byte[] content);

    /** Reads a stored file back by the path that store() returned. */
    byte[] read(String storagePath);
}
