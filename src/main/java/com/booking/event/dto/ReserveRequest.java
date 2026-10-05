package com.booking.event.dto;

import java.util.List;

public record ReserveRequest(
        List<String> seats
) {
}
