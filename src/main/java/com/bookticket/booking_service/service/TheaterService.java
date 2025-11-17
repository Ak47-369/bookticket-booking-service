package com.bookticket.booking_service.service;

import com.bookticket.booking_service.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Slf4j
public class TheaterService {
    private final RestClient theaterRestClient;

    public TheaterService(@Qualifier("theaterRestClient") RestClient theaterRestClient) {
        this.theaterRestClient = theaterRestClient;
    }

    public List<ValidSeatResponse> verifySeats(CreateBookingRequest createBookingRequest) {
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

    public List<ValidSeatResponse> lockSeats(Long showId, List<Long> seatIds) {
        LockSeatsRequest lockSeatsRequest = new LockSeatsRequest(showId, seatIds);
        List<ValidSeatResponse> lockedSeats = theaterRestClient.post()
                .uri("/api/v1/shows/internal/seats/lock")
                .body(lockSeatsRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("Error while Locking Seats : Service Call to Theater Service Failed");
                    throw new RuntimeException("Error while Locking Seats: Http Status: " + response.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {
                });
        if(lockedSeats == null || lockedSeats.isEmpty()){
            throw new RuntimeException("No Seats Locked");
        }
        log.info("Seats Locked: {}", lockedSeats);
        return lockedSeats;
    }

    public List<ValidSeatResponse> releaseSeats(Long showId, List<Long> seatIds) {
        ReleaseSeatsRequest releaseSeatsRequest = new ReleaseSeatsRequest(showId, seatIds);
        List<ValidSeatResponse> releasedSeats = theaterRestClient.post()
                .uri("/api/v1/shows/internal/seats/release")
                .body(releaseSeatsRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("Error while Releasing Seats : Service Call to Theater Service Failed");
                    throw new RuntimeException("Error while Releasing Seats: Http Status: " + response.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {
                });
        if(releasedSeats == null || releasedSeats.isEmpty()){
            throw new RuntimeException("No Seats Released");
        }
        log.info("Seats Released: {}", releasedSeats);
        return releasedSeats;
    }

    public List<ValidSeatResponse> bookSeats(Long showId, List<Long> seatIds) {
        BookSeatsRequest bookSeatsRequest = new BookSeatsRequest(showId, seatIds);
        List<ValidSeatResponse> bookedSeats = theaterRestClient.post()
                .uri("/api/v1/shows/internal/seats/book")
                .body(bookSeatsRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("Error while Booking Seats : Service Call to Theater Service Failed");
                    throw new RuntimeException("Error while Booking Seats: Http Status: " + response.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {
                });
        if(bookedSeats == null || bookedSeats.isEmpty()){
            throw new RuntimeException("No Seats Booked");
        }
        log.info("Seats Booked: {}", bookedSeats);
        return bookedSeats;
    }
}
