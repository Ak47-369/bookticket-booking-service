package com.bookticket.booking_service.service;

import com.bookticket.booking_service.dto.*;
import com.bookticket.booking_service.entity.Booking;
import com.bookticket.booking_service.entity.BookingSeat;
import com.bookticket.booking_service.enums.BookingStatus;
import com.bookticket.booking_service.exception.PaymentFailedException;
import com.bookticket.booking_service.exception.SeatLockException;
import com.bookticket.booking_service.repository.BookingRepository;
import com.bookticket.booking_service.repository.BookingSeatRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class BookingService {
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final RedisLockService redisLockService;
    private final PaymentService paymentService;
    private final TheaterService theaterService;

    public BookingService(BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          RedisLockService redisLockService,
                          PaymentService paymentService, TheaterService theaterService) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.redisLockService = redisLockService;
        this.paymentService = paymentService;
        this.theaterService = theaterService;
    }

    @Transactional
    public CreateBookingResponse createBooking(Long userId, CreateBookingRequest createBookingRequest) {
        log.info("Creating booking for user {} with {} seats in show {}",
                userId, createBookingRequest.seatIds().size(), createBookingRequest.showId());

        // Verify seats with theater service
        log.info("Verifying Seats With Theater Service: {}", createBookingRequest.seatIds());
        List<ValidSeatResponse> validSeats = theaterService.verifySeats(createBookingRequest);

        // Create booking entity first (to get booking ID for lock value)
        log.info("Creating booking entity for user {}", userId);
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setShowId(createBookingRequest.showId());
        booking.setTotalAmount(calculateTotalAmount(validSeats)); // Source of Truth - Theater Service
        booking.setStatus(BookingStatus.PENDING);
        Booking createdBooking = bookingRepository.save(booking);
        log.info("Created Booking with ID: {}", createdBooking.getId());
        List<Long> seatIds = validSeats.stream()
                .map(ValidSeatResponse::seatId)
                .toList();

        // Acquire Redis locks for all seats
        List<String> acquiredLocks = null;
        try {
            log.info("Acquiring locks for {} seats", seatIds.size());

            acquiredLocks = redisLockService.acquireSeatsLock(
                    createBookingRequest.showId(),
                    seatIds,
                    createdBooking.getId()
            );
            log.info("Successfully acquired locks for {} seats", acquiredLocks.size());
            // Mark Seats as locked
            theaterService.lockSeats(createBookingRequest.showId(), seatIds);

            // Create Booking Seats
            List<BookingSeat> bookingSeats = validSeats.stream()
                    .map(validSeat -> createBookingSeat(createdBooking, validSeat))
                    .toList();
            List<BookingSeat> savedBookingSeats = bookingSeatRepository.saveAll(bookingSeats);
            log.info("Created {} Booking Seats for Booking ID: {}", savedBookingSeats.size(), createdBooking.getId());

            // Create Stripe Checkout Session
            log.info("Creating checkout session for booking {}", createdBooking.getId());
            CheckoutSessionRequest checkoutRequest = new CheckoutSessionRequest(
                    createdBooking.getId(),
                    userId,
                    createdBooking.getTotalAmount(),
                    null,  // Use default success URL
                    null   // Use default cancel URL
            );

            try {
                CheckoutSessionResponse checkoutResponse = paymentService.createCheckoutSession(checkoutRequest);
                log.info("Checkout session created for booking {}. SessionId: {}, PaymentUrl: {}",
                        createdBooking.getId(), checkoutResponse.sessionId(), checkoutResponse.paymentUrl());

                // Map to BookingSeatResponse DTOs
                List<BookingSeatResponse> seatResponses = mapToSeatResponses(savedBookingSeats, validSeats);

                // Return booking response with payment session details
                // Booking remains in PENDING status, locks remain acquired
                // User will be redirected to paymentUrl to complete payment
                return new CreateBookingResponse(
                        createdBooking.getId(),
                        createdBooking.getUserId(),
                        createdBooking.getShowId(),
                        createdBooking.getTotalAmount(),
                        createdBooking.getStatus(),  // PENDING
                        seatResponses,
                        checkoutResponse.sessionId(),
                        checkoutResponse.paymentUrl(),
                        checkoutResponse.expiresAt()
                );

            } catch (Exception e) {
                // Checkout session creation failed - release locks and mark booking as FAILED
                log.error("Failed to create checkout session for booking {}: {}", createdBooking.getId(), e.getMessage());

                createdBooking.setStatus(BookingStatus.FAILED);
                bookingRepository.save(createdBooking);
                log.info("Marked booking {} as FAILED due to checkout session creation failure", createdBooking.getId());

                // Release locks after checkout session creation failure
                log.info("Releasing seat locks for booking {} after checkout session creation failure", createdBooking.getId());
                redisLockService.releaseSeatsLock(acquiredLocks);
                //Mark Seats as Available
                theaterService.releaseSeats(createBookingRequest.showId(), seatIds);

                // Re-throw exception to return error to user
                throw new RuntimeException("Failed to create payment session: " + e.getMessage(), e);
            }

        } catch (SeatLockException e) {
            // Lock acquisition failed - mark booking as FAILED and rollback
            log.error("Failed to acquire seat locks for booking {}: {}", createdBooking.getId(), e.getMessage());

            // Update booking status to FAILED
            createdBooking.setStatus(BookingStatus.FAILED);
            bookingRepository.save(createdBooking);
            log.info("Marked booking {} as FAILED due to lock acquisition failure", createdBooking.getId());

            // Re-throw exception to return 409 Conflict to user
            throw e;

        } catch (Exception e) {
            // Unexpected error - release locks and mark booking as FAILED
            log.error("Unexpected error during booking creation for booking {}: {}",
                    createdBooking.getId(), e.getMessage(), e);

            // Release any acquired locks
            if (acquiredLocks != null && !acquiredLocks.isEmpty()) {
                redisLockService.releaseSeatsLock(acquiredLocks);
                theaterService.releaseSeats(createBookingRequest.showId(), seatIds);
            }

            // Update booking status to FAILED
            createdBooking.setStatus(BookingStatus.FAILED);
            bookingRepository.save(createdBooking);
            log.info("Marked booking {} as FAILED due to unexpected error", createdBooking.getId());

            throw new RuntimeException("Failed to create booking due to system error", e);
        }
    }

    private BookingSeat createBookingSeat(Booking booking, ValidSeatResponse validSeat) {
        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setBooking(booking);
        bookingSeat.setSeatId(validSeat.seatId());
        bookingSeat.setSeatNumber(validSeat.seatNumber());
        bookingSeat.setSeatType(validSeat.seatType());
        bookingSeat.setPrice(validSeat.seatPrice());
        log.info("Created Booking Seat for Booking ID: {}", booking.getId());
        return bookingSeat;
    }

    private List<BookingSeatResponse> mapToSeatResponses(List<BookingSeat> bookingSeats,
                                                          List<ValidSeatResponse> validSeats) {
        return bookingSeats.stream()
                .map(bookingSeat -> {
                    // Find the corresponding ValidSeatResponse to get seat details
                    ValidSeatResponse validSeat = validSeats.stream()
                            .filter(vs -> vs.seatId().equals(bookingSeat.getSeatId()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Seat not found: " + bookingSeat.getSeatId()));

                    return new BookingSeatResponse(
                            bookingSeat.getId(),
                            bookingSeat.getSeatId(),
                            validSeat.seatNumber(),
                            validSeat.seatType(),
                            validSeat.seatPrice()
                    );
                })
                .toList();
    }

    private double calculateTotalAmount(List<ValidSeatResponse> validSeats) {
        return validSeats.stream()
                .mapToDouble(ValidSeatResponse::seatPrice)
                .sum();
    }

    /**
     * Verify payment status and complete/fail booking accordingly
     * This method should be called after user completes payment on Stripe
     *
     * @param bookingId Booking ID
     * @param sessionId Stripe Checkout Session ID
     * @return BookingResponse with updated status
     */
    @Transactional
    public BookingStatusResponse verifyAndCompleteBooking(Long bookingId, String sessionId) {
        log.info("Verifying payment and completing booking {} with session {}", bookingId, sessionId);

        // Fetch booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        // Check if booking is already processed
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("Booking {} is already in {} status. Skipping verification.",
                    bookingId, booking.getStatus());

            // Fetch booking seats for response
            List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
            List<BookingSeatResponse> seatResponses = bookingSeats.stream()
                    .map(seat -> new BookingSeatResponse(
                            seat.getId(),
                            seat.getSeatId(),
                            null,  // TODO Add Seat Details API Call
                            null,
                            null // TODO get Price from Seat Details API Call
                    ))
                    .toList();

            return new BookingStatusResponse(
                    booking.getId(),
                    booking.getUserId(),
                    booking.getShowId(),
                    booking.getTotalAmount(),
                    booking.getStatus(),
                    seatResponses
            );
        }

        // Poll payment status until COMPLETED or FAILED
        try {
            log.info("Polling payment status for session {}", sessionId);
            PaymentResponse paymentResponse = paymentService.pollPaymentStatus(sessionId);

            String paymentStatus = paymentResponse.paymentStatus();
            log.info("Payment status for booking {}: {}", bookingId, paymentStatus);

            // Fetch booking seats for response
            List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);

            // Get seat IDs for lock release
            List<Long> seatIds = bookingSeats.stream()
                    .map(BookingSeat::getSeatId)
                    .toList();

            if ("COMPLETED".equalsIgnoreCase(paymentStatus)) {
                // Payment successful - update booking to CONFIRMED and release locks
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                log.info("Booking {} confirmed successfully", bookingId);

                // Mark Seats as Booked
                theaterService.bookSeats(booking.getShowId(), seatIds);

                // Release locks
                log.info("Releasing seat locks for booking {} after successful payment", bookingId);
                redisLockService.releaseSeatsLockByIds(booking.getShowId(), seatIds);

                // Map to response
                List<BookingSeatResponse> seatResponses = bookingSeats.stream()
                        .map(seat -> new BookingSeatResponse(
                                seat.getId(),
                                seat.getSeatId(),
                                seat.getSeatNumber(),
                                seat.getSeatType(),
                                seat.getPrice()
                        ))
                        .toList();

                return new BookingStatusResponse(
                        booking.getId(),
                        booking.getUserId(),
                        booking.getShowId(),
                        booking.getTotalAmount(),
                        booking.getStatus(),
                        seatResponses
                );

            } else {
                // Payment failed - update booking to FAILED and release locks
                booking.setStatus(BookingStatus.FAILED);
                bookingRepository.save(booking);
                log.warn("Booking {} marked as FAILED due to payment failure", bookingId);

                // Release locks
                log.info("Releasing seat locks for booking {} after payment failure", bookingId);
                redisLockService.releaseSeatsLockByIds(booking.getShowId(), seatIds);
                //Mark Seats as Available
                theaterService.releaseSeats(booking.getShowId(), seatIds);

                throw new PaymentFailedException(
                        paymentResponse.message() != null ?
                                paymentResponse.message() : "Payment failed",
                        paymentStatus,
                        paymentResponse.transactionId()
                );
            }

//        } catch (PaymentFailedException e) {
//            // Re-throw PaymentFailedException
//            throw e;
        } catch (Exception e) {
            // Unexpected error during payment verification
            log.error("Unexpected error during payment verification for booking {}: {}",
                    bookingId, e.getMessage(), e);

            // Mark booking as FAILED and release locks
            booking.setStatus(BookingStatus.FAILED);
            bookingRepository.save(booking);

            // Release locks
            List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
            List<Long> seatIds = bookingSeats.stream()
                    .map(BookingSeat::getSeatId)
                    .toList();

            log.info("Releasing seat locks for booking {} after verification error", bookingId);
            redisLockService.releaseSeatsLockByIds(booking.getShowId(), seatIds);
            //Mark Seats as Available
            theaterService.releaseSeats(booking.getShowId(), seatIds);

            throw new RuntimeException("Failed to verify payment: " + e.getMessage(), e);
        }
    }
}
