package com.docflow.pipeline.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A purchase order the company issued. In production these are synced from the ERP system. */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "po_number", nullable = false)
    private String poNumber;

    @Column(nullable = false)
    private String vendor;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    protected PurchaseOrder() {
        // for JPA
    }

    public PurchaseOrder(String id, String tenantId, String poNumber, String vendor, BigDecimal amount, String currency) {
        this.id = id;
        this.tenantId = tenantId;
        this.poNumber = poNumber;
        this.vendor = vendor;
        this.amount = amount;
        this.currency = currency;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getPoNumber() { return poNumber; }
    public String getVendor() { return vendor; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
}
