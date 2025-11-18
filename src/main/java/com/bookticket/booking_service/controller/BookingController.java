package com.bookticket.booking_service.controller;

import com.bookticket.booking_service.dto.BookingStatusResponse;
import com.bookticket.booking_service.dto.CreateBookingResponse;
import com.bookticket.booking_service.dto.CreateBookingRequest;
import com.bookticket.booking_service.dto.SeatDetailsResponse;
import com.bookticket.booking_service.security.UserPrincipal;
import com.bookticket.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@Slf4j
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Create a new booking with seat locks and payment session
     * Returns booking with PENDING status and payment URL
     * User should be redirected to paymentUrl to complete payment
     */
    @PostMapping
    public ResponseEntity<CreateBookingResponse> createBooking(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                               @Valid @RequestBody CreateBookingRequest createBookingRequest) {
        log.info("Creating booking for user {} with request: {}", userPrincipal.getUserId(), createBookingRequest);
        CreateBookingResponse response = bookingService.createBooking(userPrincipal.getUserId(), createBookingRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Verify payment and complete booking
     * This endpoint should be called after user completes payment on Stripe
     *
     * @param bookingId Booking ID
     * @param sessionId Stripe Checkout Session ID
     * @return BookingResponse with updated status (CONFIRMED or FAILED)
     */
    @GetMapping("/{bookingId}/verify-payment")
    public ResponseEntity<BookingStatusResponse> verifyPayment(
            @PathVariable Long bookingId,
            @RequestParam String sessionId) {
        log.info("Verifying payment for booking {} with session {}", bookingId, sessionId);
        BookingStatusResponse response = bookingService.verifyAndCompleteBooking(bookingId, sessionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bookingId}/seats")
    public ResponseEntity<List<SeatDetailsResponse>> getSeatDetails(
            @PathVariable Long bookingId) {
        log.info("Getting seat Details for booking {}", bookingId);
        List<SeatDetailsResponse> seatDetails = bookingService.getSeatDetailsByBookingId(bookingId);
        return ResponseEntity.ok(seatDetails);
    }
}
