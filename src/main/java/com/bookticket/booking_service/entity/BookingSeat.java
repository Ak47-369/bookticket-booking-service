package com.bookticket.booking_service.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "booking_seats")
public class BookingSeat extends Auditable{
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
    @Column(nullable = false)
    private Long seatId;
}
