// getId: returns the known object's generated database id.
// getName: returns the known object's name.
// getKind: returns what kind of fixed object it is, such as a platform, breakwater or buoy.
// getLatitude: returns the object's latitude in degrees.
// getLongitude: returns the object's longitude in degrees.
// getMmsi: returns the MMSI the object transmits on, or null when it sends no AIS.

package com.wisertech.shipviewer.knownobject.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "known_object")
public class KnownObject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 10)
    private String mmsi;

    protected KnownObject() {
    }

    public KnownObject(String name, String kind, BigDecimal latitude, BigDecimal longitude, String mmsi) {
        this.name = name;
        this.kind = kind;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mmsi = mmsi;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getMmsi() {
        return mmsi;
    }
}
