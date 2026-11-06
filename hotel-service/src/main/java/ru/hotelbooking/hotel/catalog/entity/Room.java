package ru.hotelbooking.hotel.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_rooms_hotel_number",
                        columnNames = {"hotel_id", "number"}))
@Check(name = "ck_rooms_times_booked_non_negative", constraints = "times_booked >= 0")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "hotel_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_rooms_hotel"))
    private Hotel hotel;

    @Column(nullable = false, length = 64)
    private String number;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "times_booked", nullable = false)
    private long timesBooked;

    protected Room() {}

    public Room(Hotel hotel, String number) {
        this.hotel = hotel;
        this.number = number;
        this.available = true;
        this.timesBooked = 0;
    }

    public Long getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public String getNumber() {
        return number;
    }

    public boolean isAvailable() {
        return available;
    }

    public long getTimesBooked() {
        return timesBooked;
    }

    public void recordAllocation() {
        timesBooked++;
    }

    public void forgetAllocation() {
        if (timesBooked > 0) {
            timesBooked--;
        }
    }

    public void updateDetails(String number, boolean available) {
        this.number = number;
        this.available = available;
    }
}
