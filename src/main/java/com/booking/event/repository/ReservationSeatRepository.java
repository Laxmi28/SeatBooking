package com.booking.event.repository;

import com.booking.event.entity.ReservationSeat;
import com.booking.event.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationSeatRepository extends
        JpaRepository<ReservationSeat, ReservationSeatId> {

    List<ReservationSeat> findByReservationId(UUID reservationId);
}
