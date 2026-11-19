package com.freightfox.dispatch.entity;

import com.freightfox.dispatch.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder implements Persistable<String> {

    @Id
    @Column(length = 64)
    private String orderId;

    private double latitude;
    private double longitude;

    @Column(length = 512, nullable = false)
    private String address;

    private double packageWeight;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private Priority priority;

    // Lets Spring Data call persist() directly for new rows instead of an extra SELECT (merge).
    @Transient
    private boolean isNew = true;

    protected DeliveryOrder() {
    }

    public DeliveryOrder(String orderId) {
        this.orderId = orderId;
    }

    /** Overwrites all mutable fields - used for upsert. */
    public void update(double latitude, double longitude, String address, double packageWeight, Priority priority) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.packageWeight = packageWeight;
        this.priority = priority;
    }

    @Override
    public String getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public String getOrderId() {
        return orderId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public double getPackageWeight() {
        return packageWeight;
    }

    public Priority getPriority() {
        return priority;
    }
}
