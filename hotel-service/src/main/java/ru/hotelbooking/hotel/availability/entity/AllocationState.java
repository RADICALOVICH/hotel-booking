package ru.hotelbooking.hotel.availability.entity;

/** ACTIVE holds the dates; REJECTED and RELEASED are final and never become ACTIVE again. */
public enum AllocationState {
    ACTIVE,
    REJECTED,
    RELEASED
}
