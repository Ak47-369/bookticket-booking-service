package com.bookticket.booking_service.controller;

import com.bookticket.booking_service.dto.BookingResponse;
import com.bookticket.booking_service.dto.CreateBookingRequest;
import com.bookticket.booking_service.security.UserPrincipal;
import com.bookticket.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                         @Valid @RequestBody CreateBookingRequest createBookingRequest) {
       return new ResponseEntity<>(bookingService.createBooking(userPrincipal.getUserId(), createBookingRequest), HttpStatus.CREATED);
    }
}
