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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
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
    private final KafkaTemplate<String, BookingSuccessEvent> kafkaSuccessTemplate;
    private final KafkaTemplate<String, BookingFailedEvent> kafkaFailedTemplate;
    private final NotificationService notificationService;
    private final DeadLetterQueueService deadLetterQueueService;

    public BookingService(BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          RedisLockService redisLockService,
                          PaymentService paymentService, TheaterService theaterService,
                          KafkaTemplate<String, BookingSuccessEvent> kafkaSuccessTemplate,
                          KafkaTemplate<String, BookingFailedEvent> kafkaFailedTemplate,
                          NotificationService notificationService,
                          DeadLetterQueueService deadLetterQueueService) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.redisLockService = redisLockService;
        this.paymentService = paymentService;
        this.theaterService = theaterService;
        this.kafkaSuccessTemplate = kafkaSuccessTemplate;
        this.kafkaFailedTemplate = kafkaFailedTemplate;
        this.notificationService = notificationService;
        this.deadLetterQueueService = deadLetterQueueService;
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
                sendBookingFailedEvent(createdBooking, "Failed to create payment session");

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
            sendBookingFailedEvent(createdBooking, "Seats no longer available");

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
            sendBookingFailedEvent(createdBooking, e.getMessage());

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
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        // Get seat IDs for lock release
        List<Long> seatIds = bookingSeats.stream()
                .map(BookingSeat::getSeatId)
                .toList();

        // Check if booking is already processed
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("Booking {} is already in {} status. Skipping verification.",
                    bookingId, booking.getStatus());

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
        }

        // Poll payment status until COMPLETED or FAILED
        try {
            log.info("Polling payment status for session {}", sessionId);
            PaymentResponse paymentResponse = paymentService.pollPaymentStatus(sessionId);

            String paymentStatus = paymentResponse.paymentStatus();
            log.info("Payment status for booking {}: {}", bookingId, paymentStatus);

            if ("COMPLETED".equalsIgnoreCase(paymentStatus)) {
                // Payment successful - update booking to CONFIRMED and release locks
                booking.setStatus(BookingStatus.CONFIRMED);
                Booking confirmedBooking = bookingRepository.save(booking);
                log.info("Booking {} confirmed successfully", bookingId);

                // Mark Seats as Booked
                theaterService.bookSeats(confirmedBooking.getShowId(), seatIds);

                // Release locks
                log.info("Releasing seat locks for booking {} after successful payment", bookingId);
                redisLockService.releaseSeatsLockByIds(confirmedBooking.getShowId(), seatIds);

                //TODO Push to booking_success topic in Kafka
                sendBookingSuccessEvent(confirmedBooking);

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
                        confirmedBooking.getId(),
                        confirmedBooking.getUserId(),
                        confirmedBooking.getShowId(),
                        confirmedBooking.getTotalAmount(),
                        confirmedBooking.getStatus(),
                        seatResponses
                );

            } else {
                // Payment failed - update booking to FAILED and release locks
                booking.setStatus(BookingStatus.FAILED);
                Booking failedBooking = bookingRepository.save(booking);
                log.warn("Booking {} marked as FAILED due to payment failure", bookingId);

                // Release locks
                log.info("Releasing seat locks for booking {} after payment failure", bookingId);
                redisLockService.releaseSeatsLockByIds(failedBooking.getShowId(), seatIds);
                //Mark Seats as Available
                theaterService.releaseSeats(failedBooking.getShowId(), seatIds);

                // TODO - Send booking_failed event to kafka
                sendBookingFailedEvent(failedBooking, paymentResponse.message());

                throw new PaymentFailedException(
                        paymentResponse.message() != null ?
                                paymentResponse.message() : "Payment failed",
                        paymentStatus,
                        paymentResponse.transactionId()
                );
            }

        } catch (PaymentFailedException e) {
            // Mark booking as FAILED and release locks
            booking.setStatus(BookingStatus.FAILED);
            Booking failedBooking = bookingRepository.save(booking);

            log.info("Releasing seat locks for booking {} after verification error", bookingId);
            redisLockService.releaseSeatsLockByIds(booking.getShowId(), seatIds);
            //Mark Seats as Available
            theaterService.releaseSeats(booking.getShowId(), seatIds);

            // TODO - Send booking_failed event to kafka
            sendBookingFailedEvent(failedBooking, e.getMessage());
            // Re-throw PaymentFailedException
            throw e;
        }
        catch (Exception e) {
            // Unexpected error during payment verification
            log.error("Unexpected error during payment verification for booking {}: {}",
                    bookingId, e.getMessage(), e);
            throw new RuntimeException("Failed to verify payment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Send booking success event with retry mechanism
     * Retries 3 times with exponential backoff (1s, 2s, 4s)
     * If all retries fail, stores in Dead Letter Queue
     */
    @Async("bookingEventExecutor")
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void sendBookingSuccessEvent(Booking confirmedBooking) {
        BookingSuccessEvent bookingSuccessEvent = new BookingSuccessEvent(
                confirmedBooking.getId(),
                confirmedBooking.getUserId(),
                confirmedBooking.getShowId(),
                confirmedBooking.getTotalAmount()
        );

        try {
            // Try Kafka first
            kafkaSuccessTemplate.send("booking_success", bookingSuccessEvent);
            log.info("Sent booking success event to Kafka for booking {}", confirmedBooking.getId());

        } catch (Exception e) {
            log.error("Failed to send booking success event to Kafka for booking {}: {}",
                    confirmedBooking.getId(), e.getMessage());

            // Try REST fallback
            log.info("[FallBack] : Attempting REST fallback for booking success event, booking {}",
                    confirmedBooking.getId());
            notificationService.sendBookingSuccessEvent(bookingSuccessEvent);
            log.info("Successfully sent booking success event via REST fallback for booking {}",
                    confirmedBooking.getId());
        }
    }

    /**
     * Recovery method called when all retry attempts are exhausted
     * Stores the failed event in Dead Letter Queue for manual processing
     */
    @Recover
    public void recoverBookingSuccessEvent(Exception e, Booking confirmedBooking) {
        log.error("All retry attempts exhausted for booking success event, booking {}: {}",
                confirmedBooking.getId(), e.getMessage());

        // Store in DLQ for manual retry
        deadLetterQueueService.storeFailedSuccessEvent(
                confirmedBooking.getId(),
                confirmedBooking.getUserId(),
                confirmedBooking.getShowId(),
                confirmedBooking.getTotalAmount(),
                e.getMessage()
        );

        log.warn("Booking success event stored in DLQ for booking {}", confirmedBooking.getId());
    }
    
    /**
     * Send booking failed event with retry mechanism
     * Retries 3 times with exponential backoff (1s, 2s, 4s)
     * If all retries fail, stores in Dead Letter Queue
     */
    @Async("bookingEventExecutor")
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void sendBookingFailedEvent(Booking failedBooking, String reason) {
        BookingFailedEvent bookingFailedEvent = new BookingFailedEvent(
                failedBooking.getId(),
                failedBooking.getUserId(),
                failedBooking.getShowId(),
                failedBooking.getTotalAmount(),
                reason
        );

        try {
            // Try Kafka first
            kafkaFailedTemplate.send("booking_failed", bookingFailedEvent);
            log.info("Sent booking failed event to Kafka for booking {}", failedBooking.getId());

        } catch (Exception e) {
            log.error("Failed to send booking failed event to Kafka for booking {}: {}",
                    failedBooking.getId(), e.getMessage());

            // Try REST fallback
            log.info("[FallBack] : Attempting REST fallback for booking failed event, booking {}",
                    failedBooking.getId());
            notificationService.sendBookingFailedEvent(bookingFailedEvent);
            log.info("Successfully sent booking failed event via REST fallback for booking {}",
                    failedBooking.getId());
        }
    }

    /**
     * Recovery method called when all retry attempts are exhausted
     * Stores the failed event in Dead Letter Queue for manual processing
     */
    @Recover
    public void recoverBookingFailedEvent(Exception e, Booking failedBooking, String reason) {
        log.error("All retry attempts exhausted for booking failed event, booking {}: {}",
                failedBooking.getId(), e.getMessage());

        // Store in DLQ for manual retry
        deadLetterQueueService.storeFailedFailureEvent(
                failedBooking.getId(),
                failedBooking.getUserId(),
                failedBooking.getShowId(),
                failedBooking.getTotalAmount(),
                reason,
                e.getMessage()
        );

        log.warn("Booking failed event stored in DLQ for booking {}", failedBooking.getId());
    }

    public List<SeatDetailsResponse> getSeatDetailsByBookingId(Long bookingId) {
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        return bookingSeats.stream()
                .map(bookingSeat -> new SeatDetailsResponse(
                        bookingSeat.getSeatId(),
                        bookingSeat.getSeatNumber(),
                        bookingSeat.getSeatType(),
                        bookingSeat.getPrice()
                ))
                .toList();
    }
}
