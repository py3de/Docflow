package com.docflow.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldCorrectionRepository extends JpaRepository<FieldCorrection, String> {

    List<FieldCorrection> findByDocumentId(String documentId);
}
