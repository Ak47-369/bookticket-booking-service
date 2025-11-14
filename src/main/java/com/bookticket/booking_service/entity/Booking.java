package com.bookticket.booking_service.entity;

import com.bookticket.booking_service.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "bookings")
@Data
public class Booking extends Auditable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;
    @Column(nullable = false)
    private Long userId; // From X-User-Id header
    @Column(nullable = false)
    private Long showId; // From theater service
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status;
    @Column(nullable = false)
    private double totalAmount;
}
