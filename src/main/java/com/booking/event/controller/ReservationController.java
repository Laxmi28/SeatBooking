package com.booking.event.controller;

import com.booking.event.dto.ReservationResponse;
import com.booking.event.dto.ReserveRequest;
import com.booking.event.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService){
        this.reservationService = reservationService;
    }

    @PostMapping("/{showId}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(
            @PathVariable(value = "showId") Long showId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReserveRequest request
            ){
        return reservationService.reserve(showId,userId,request,idempotencyKey);
    }

    @DeleteMapping("/reservations/{reservationId}")
    public void cancelReservation(
            @PathVariable UUID reservationId,
            @RequestHeader("X-User-Id") String userId
    ){
        reservationService.cancelReservation(reservationId,userId);
    }


}
