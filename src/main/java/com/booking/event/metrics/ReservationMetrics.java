package com.booking.event.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReservationMetrics {

    private final Counter confirmedCounter;
    private final Counter seatTakenCounter;
    private final Counter bookingLimitCounter;
    private final Counter cancelledCounter;

    private final AtomicInteger availableSeats = new AtomicInteger(0);

    public ReservationMetrics(MeterRegistry meterRegistry) {

        confirmedCounter = Counter.builder("reservation_confirmed_total")
                .description("Total number of confirmed reservations")
                .register(meterRegistry);

        seatTakenCounter = Counter.builder("reservation_declined_total")
                .tag("reason", "seat_taken")
                .description("Reservations declined because seats were already taken")
                .register(meterRegistry);

        bookingLimitCounter = Counter.builder("reservation_declined_total")
                .tag("reason", "booking_limit")
                .description("Reservations declined because user booking limit was exceeded")
                .register(meterRegistry);

        cancelledCounter = Counter.builder("reservation_cancelled_total")
                .description("Total number of cancelled reservations")
                .register(meterRegistry);

        Gauge.builder(
                        "seats_available",
                        availableSeats,
                        AtomicInteger::get
                )
                .description("Current number of available seats")
                .register(meterRegistry);
    }

    public void reservationConfirmed() {
        confirmedCounter.increment();
    }

    public void seatTaken() {
        seatTakenCounter.increment();
    }

    public void bookingLimitExceeded() {
        bookingLimitCounter.increment();
    }

    public void reservationCancelled() {
        cancelledCounter.increment();
    }

    public void setAvailableSeats(int count) {
        availableSeats.set(count);
    }
}