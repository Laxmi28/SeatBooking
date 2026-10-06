package com.booking.event.repository;

import com.booking.event.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM Seat s
            WHERE s.id = :seatId
         """)
   Optional<Seat> findByIdForUpdate(@Param("seatId") Long seatId);

    Optional<Seat>  findByShowIdAndSeatNumber(Long showId , String seatNumber);



}
