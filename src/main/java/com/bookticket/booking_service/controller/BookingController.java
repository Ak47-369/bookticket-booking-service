package com.bookticket.booking_service.controller;

import com.bookticket.booking_service.dto.BookingStatusResponse;
import com.bookticket.booking_service.dto.CreateBookingResponse;
import com.bookticket.booking_service.dto.CreateBookingRequest;
import com.bookticket.booking_service.dto.SeatDetailsResponse;
import com.bookticket.booking_service.security.UserPrincipal;
import com.bookticket.booking_service.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Booking Controller", description = "APIs for creating and managing ticket bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(
            summary = "Create a new booking",
            description = "Creates a new booking, locks the selected seats, and generates a payment session. The user should be redirected to the `paymentUrl` to complete the transaction.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Booking created successfully, pending payment",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateBookingResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid booking data or seats not available",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Seats already locked by another booking",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "503", description = "Service unavailable",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "429", description = "Too many requests",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Not found",
                            content = @Content(mediaType = "application/json"))
            }
    )
    @PostMapping
    public ResponseEntity<CreateBookingResponse> createBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateBookingRequest createBookingRequest) {
        log.info("Creating booking for user {} with request: {}", userPrincipal.getUserId(), createBookingRequest);
        CreateBookingResponse response = bookingService.createBooking(userPrincipal.getUserId(), createBookingRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Verify payment and complete booking",
            description = "Verifies the Stripe payment session and updates the booking status to CONFIRMED or FAILED. This endpoint should be called as the callback/success URL after payment.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment verified and booking status updated",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingStatusResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid booking or session ID",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Booking not found",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "503", description = "Service unavailable",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "429", description = "Too many requests",
                            content = @Content(mediaType = "application/json"))
            }
    )
    @GetMapping("/{bookingId}/verify-payment")
    public ResponseEntity<BookingStatusResponse> verifyPayment(
            @Parameter(description = "ID of the booking to verify", required = true)
            @PathVariable Long bookingId,
            @Parameter(description = "Stripe checkout session ID", required = true)
            @RequestParam String sessionId) {
        log.info("Verifying payment for booking {} with session {}", bookingId, sessionId);
        BookingStatusResponse response = bookingService.verifyAndCompleteBooking(bookingId, sessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get seat details for a booking",
            description = "Retrieves the specific seat numbers (e.g., A1, B5) associated with a confirmed booking.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Seat details retrieved successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SeatDetailsResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Booking not found",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "503", description = "Service unavailable",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "429", description = "Too many requests",
                            content = @Content(mediaType = "application/json"))
            }
    )
    @GetMapping("/{bookingId}/seats")
    public ResponseEntity<List<SeatDetailsResponse>> getSeatDetails(
            @Parameter(description = "ID of the booking to retrieve seat details for", required = true)
            @PathVariable Long bookingId) {
        log.info("Getting seat Details for booking {}", bookingId);
        List<SeatDetailsResponse> seatDetails = bookingService.getSeatDetailsByBookingId(bookingId);
        return ResponseEntity.ok(seatDetails);
    }
}
