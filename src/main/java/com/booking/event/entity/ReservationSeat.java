package com.booking.event.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table( name = "reservation_seats")
@IdClass(ReservationSeatId.class)
@Data
public class ReservationSeat {

    @Id
    @Column( name = "reservation_id" , nullable = false)
    private UUID reservationId;
    @Id
    @Column( name = "seat_id" , nullable = false)
    private Long seatId;

}
