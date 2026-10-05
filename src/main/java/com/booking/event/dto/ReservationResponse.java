package com.booking.event.dto;

import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        Long showId,
        String userId,
        List<String> seats,
        Long amountPaise,
        String status
) {
}
