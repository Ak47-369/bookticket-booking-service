# BookTicket :: Services :: Booking Service

## Overview

The **Booking Service** is the transactional engine of the BookTicket platform. It orchestrates the complex, multi-step process of reserving seats and creating a confirmed booking. It acts as a central coordinator, interacting with multiple other services to ensure a booking is processed reliably and consistently.

## Core Responsibilities

-   **Booking Orchestration:** Manages the end-to-end lifecycle of a booking, from `PENDING` to `CONFIRMED` or `FAILED`.
-   **Seat Reservation:** Implements a concurrency-safe mechanism to temporarily lock seats while a user completes payment.
-   **Payment Initiation:** Coordinates with the `Payment Service` to create and process payments.
-   **Asynchronous Notifications:** Publishes events to a message queue (Kafka) to trigger downstream processes, such as sending confirmation emails, without blocking the core booking flow.

## Architecture & Saga Orchestration

The booking process is a classic example of a **Saga pattern**, orchestrated by this service.
<img width="3146" height="1998" alt="Booking Service" src="https://github.com/user-attachments/assets/cc6c8a91-8a4c-423c-bdb7-67c7f0f1265e" />


### How It Works

1.  **Seat Locking:** To prevent double-bookings, the service uses a **distributed lock in Redis**. When a user selects seats, a lock with a short TTL (e.g., 5 minutes) is acquired. This provides a high-performance, scalable solution for managing concurrency.
2.  **Payment Orchestration:** The service makes a synchronous call to the `Payment Service` to create a payment intent. The result of the payment is communicated back asynchronously via a webhook or message queue.
3.  **Asynchronous Events:** Upon successful confirmation, the service publishes a `BookingConfirmationEvent` to an **Apache Kafka** topic. This decouples the booking process from downstream actions like sending notifications. If the `Notification Service` is temporarily unavailable, the booking can still be successfully confirmed, and the notification will be sent later.
4.  **Data Storage:** Booking records are stored in a **PostgreSQL** database to ensure the highest level of transactional integrity and data consistency.

## Key Dependencies

-   **Spring Boot Starter Data JPA:** For database interaction with PostgreSQL.
-   **Spring Boot Starter Data Redis Reactive:** For implementing distributed locking.
-   **Spring Kafka:** For publishing asynchronous events.
-   **Spring Retry:** Used to handle transient failures when communicating with other services.
-   **Eureka Discovery Client:** To discover and communicate with other services like `Payment Service` and `Theater Service`.

## API Endpoints

The service's endpoints are exposed through the API Gateway and are secured, requiring a valid JWT.

-   `POST /api/v1/bookings`: The primary endpoint to create a new booking. The request body should contain the `showId` and a list of `seatIds`. This initiates the seat-locking and payment-creation process.
-   `GET /api/v1/bookings/{bookingId}/verify-payment`: Poll the payment status from Payment Service, and update the booking status accordingly.
<!-- -   `GET /api/v1/bookings/verify`: The endpoint the user is redirected back to after completing the payment flow on Stripe. It takes `bookingId` and `sessionId` as query parameters to verify and finalize the booking. -->
-   `GET /api/v1/bookings/{id}`: Fetches the complete details of a specific booking by its ID.
-   `GET /api/v1/bookings/{id}/seats`: Fetches the specific seat details (number, type, price) associated with a particular booking.
