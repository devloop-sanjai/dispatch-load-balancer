package com.freightfox.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "vehicles")
public class Vehicle implements Persistable<String> {

    @Id
    @Column(length = 64)
    private String vehicleId;

    private double capacity;
    private double currentLatitude;
    private double currentLongitude;

    @Column(length = 512, nullable = false)
    private String currentAddress;

    @Transient
    private boolean isNew = true;

    protected Vehicle() {
    }

    public Vehicle(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    /** Overwrites all mutable fields - used for upsert. */
    public void update(double capacity, double currentLatitude, double currentLongitude, String currentAddress) {
        this.capacity = capacity;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.currentAddress = currentAddress;
    }

    @Override
    public String getId() {
        return vehicleId;
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

    public String getVehicleId() {
        return vehicleId;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getCurrentLatitude() {
        return currentLatitude;
    }

    public double getCurrentLongitude() {
        return currentLongitude;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }
}
