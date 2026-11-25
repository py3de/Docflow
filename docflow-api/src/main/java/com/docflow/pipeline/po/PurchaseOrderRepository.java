package com.docflow.pipeline.po;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {

    List<PurchaseOrder> findByTenantId(String tenantId);
}
