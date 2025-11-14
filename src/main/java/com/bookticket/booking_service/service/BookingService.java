package com.bookticket.booking_service.service;

import com.bookticket.booking_service.dto.BookingResponse;
import com.bookticket.booking_service.dto.BookingSeatResponse;
import com.bookticket.booking_service.dto.CreateBookingRequest;
import com.bookticket.booking_service.dto.ValidSeatResponse;
import com.bookticket.booking_service.dto.VerifySeatsRequest;
import com.bookticket.booking_service.entity.Booking;
import com.bookticket.booking_service.entity.BookingSeat;
import com.bookticket.booking_service.enums.BookingStatus;
import com.bookticket.booking_service.exception.SeatLockException;
import com.bookticket.booking_service.repository.BookingRepository;
import com.bookticket.booking_service.repository.BookingSeatRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Slf4j
public class BookingService {
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final RestClient theaterRestClient;
    private final RedisLockService redisLockService;

    public BookingService(BookingRepository bookingRepository, BookingSeatRepository bookingSeatRepository,
                          RestClient theaterRestClient,
                          RedisLockService redisLockService) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.theaterRestClient = theaterRestClient;
        this.redisLockService = redisLockService;
    }

    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest createBookingRequest) {
        log.info("Creating booking for user {} with {} seats in show {}",
                userId, createBookingRequest.seatIds().size(), createBookingRequest.showId());

        // Verify seats with theater service
        log.info("Verifying Seats With Theater Service: {}", createBookingRequest.seatIds());
        List<ValidSeatResponse> validSeats = verifySeats(createBookingRequest);

        // Create booking entity first (to get booking ID for lock value)
        log.info("Creating booking entity for user {}", userId);
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setShowId(createBookingRequest.showId());
        booking.setTotalAmount(calculateTotalAmount(validSeats)); // Source of Truth - Theater Service
        booking.setStatus(BookingStatus.PENDING);
        Booking createdBooking = bookingRepository.save(booking);
        log.info("Created Booking with ID: {}", createdBooking.getId());

        // Acquire Redis locks for all seats
        List<String> acquiredLocks = null;
        try {
            List<Long> seatIds = validSeats.stream()
                    .map(ValidSeatResponse::seatId)
                    .toList();

            acquiredLocks = redisLockService.acquireSeatsLock(
                    createBookingRequest.showId(),
                    seatIds,
                    createdBooking.getId()
            );
            log.info("Successfully acquired locks for {} seats", acquiredLocks.size());

            // Create Booking Seats
            List<BookingSeat> bookingSeats = validSeats.stream()
                    .map(validSeat -> createBookingSeat(createdBooking, validSeat))
                    .toList();
            List<BookingSeat> savedBookingSeats = bookingSeatRepository.saveAll(bookingSeats);
            log.info("Created {} Booking Seats for Booking ID: {}", savedBookingSeats.size(), createdBooking.getId());

            // Map to BookingSeatResponse DTOs
            List<BookingSeatResponse> seatResponses = mapToSeatResponses(savedBookingSeats, validSeats);

            return new BookingResponse(
                    createdBooking.getId(),
                    createdBooking.getUserId(),
                    createdBooking.getShowId(),
                    createdBooking.getTotalAmount(),
                    createdBooking.getStatus(),
                    seatResponses
            );

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
            }

            // Update booking status to FAILED
            createdBooking.setStatus(BookingStatus.FAILED);
            bookingRepository.save(createdBooking);
            log.info("Marked booking {} as FAILED due to unexpected error", createdBooking.getId());

            throw new RuntimeException("Failed to create booking due to system error", e);
        }
    }

    private List<ValidSeatResponse> verifySeats(CreateBookingRequest createBookingRequest) {
        VerifySeatsRequest verifySeatsRequest = new VerifySeatsRequest(
                createBookingRequest.showId(),
                createBookingRequest.seatIds()
        );
        List<ValidSeatResponse> validSeats = theaterRestClient.post()
                .uri("/api/v1/shows/internal/seats/verify")
                .body(verifySeatsRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("Error while Verifying Seats : Service Call to Theater Service Failed");
                    throw new RuntimeException("Error while Verifying Seats: Http Status: " + response.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {
                });
        if(validSeats == null || validSeats.isEmpty()){
            throw new RuntimeException("No Valid Seats Found");
        }
        log.info("Valid Seats Found: {}", validSeats);
        return validSeats;
    }

    private BookingSeat createBookingSeat(Booking booking, ValidSeatResponse validSeat) {
        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setBooking(booking);
        bookingSeat.setSeatId(validSeat.seatId());
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
}
