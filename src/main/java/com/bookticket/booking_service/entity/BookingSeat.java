package com.bookticket.booking_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat extends Auditable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_seat_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
    @Column(name = "seat_id", nullable = false)
    private Long seatId;
    // Denormalize for performance
    @Column(name = "seat_number", nullable = false)
    private String seatNumber;
    @Column(name = "seat_type", nullable = false)
    private String seatType;
    @Column(name = "price", nullable = false)
    private Double price; // Need to store this, what if the price changes, during booking
}
