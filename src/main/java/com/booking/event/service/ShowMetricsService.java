package com.booking.event.service;

import com.booking.event.entity.SeatStatus;
import com.booking.event.metrics.ReservationMetrics;
import com.booking.event.repository.SeatRepository;
import org.springframework.stereotype.Service;

@Service
public class ShowMetricsService {
    private final SeatRepository seatRepository;
    private final ReservationMetrics reservationMetrics;

    public ShowMetricsService(SeatRepository seatRepository, ReservationMetrics reservationMetrics){
        this.seatRepository = seatRepository;
        this.reservationMetrics = reservationMetrics;
    }

    public  void updateAvailableSeats(Long showId){
         long available = seatRepository.countByShowIdAndStatus(showId, SeatStatus.AVAILABLE);
    }
}
