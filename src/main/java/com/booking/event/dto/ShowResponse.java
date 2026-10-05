package com.booking.event.dto;

import java.util.List;

public record ShowResponse(
        Long showId,
        String name,
        Long pricePaise,
        int totalSeats,
        int available,
        int held,
        int confirmed,
        List<SeatResponse> seats
) {
}
