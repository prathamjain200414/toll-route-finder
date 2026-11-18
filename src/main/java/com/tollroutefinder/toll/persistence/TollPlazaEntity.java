package com.tollroutefinder.toll.persistence;

import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.toll.TollPlaza;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "toll_plaza")
public class TollPlazaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plaza_key", nullable = false, unique = true, length = 160)
    private String plazaKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String state;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TollPlazaEntity() {
        // for JPA
    }

    public TollPlaza toDomain() {
        return new TollPlaza(plazaKey, name, new GeoPoint(latitude, longitude), state);
    }

    public Long getId() {
        return id;
    }

    public String getPlazaKey() {
        return plazaKey;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
