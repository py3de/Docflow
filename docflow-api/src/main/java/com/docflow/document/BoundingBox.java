package com.docflow.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Where a field sits on the page, in relative coordinates (0..1). */
@Embeddable
public class BoundingBox {

    @Column(name = "box_x")
    private Double x;

    @Column(name = "box_y")
    private Double y;

    @Column(name = "box_w")
    private Double w;

    @Column(name = "box_h")
    private Double h;

    protected BoundingBox() {
        // for JPA
    }

    public BoundingBox(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Hibernate instantiates an embeddable with all-null columns as an empty object. */
    public boolean isEmpty() {
        return x == null || y == null || w == null || h == null;
    }

    public Double getX() { return x; }
    public Double getY() { return y; }
    public Double getW() { return w; }
    public Double getH() { return h; }
}
