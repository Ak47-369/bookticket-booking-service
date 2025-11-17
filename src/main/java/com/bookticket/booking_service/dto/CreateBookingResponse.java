package com.bookticket.booking_service.dto;

import com.bookticket.booking_service.enums.BookingStatus;

import java.util.List;


public record CreateBookingResponse(
        Long bookingId,
        Long userId,
        Long showId,
        double totalAmount,
        BookingStatus status,
        List<BookingSeatResponse> seats,
        String paymentSessionId,    // Stripe Checkout Session ID (for pending payments)
        String paymentUrl,           // URL to redirect user for payment (for pending payments)
        Long paymentExpiresAt        // Unix timestamp when payment session expires
) {
    // Constructor for pending bookings with payment session
    public CreateBookingResponse(Long bookingId, Long userId, Long showId, double totalAmount,
                                 BookingStatus status, List<BookingSeatResponse> seats,
                                 String paymentSessionId, String paymentUrl, Long paymentExpiresAt) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.showId = showId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.seats = seats;
        this.paymentSessionId = paymentSessionId;
        this.paymentUrl = paymentUrl;
        this.paymentExpiresAt = paymentExpiresAt;
    }

    // Constructor for completed/failed bookings without payment session
    public CreateBookingResponse(Long bookingId, Long userId, Long showId, double totalAmount,
                                 BookingStatus status, List<BookingSeatResponse> seats) {
        this(bookingId, userId, showId, totalAmount, status, seats, null, null, null);
    }
}
