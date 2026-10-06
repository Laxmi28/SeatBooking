package com.booking.event.service;

import com.booking.event.dto.CreateShowRequest;
import com.booking.event.entity.Seat;
import com.booking.event.entity.SeatStatus;
import com.booking.event.entity.Show;
import com.booking.event.repository.SeatRepository;
import com.booking.event.repository.ShowRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private static final Integer perUserLimit = 4;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository){
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public Show createShow(CreateShowRequest request){
        Show show =new Show();
        show.setName(request.name());
        show.setPriceInPaise(request.pricePaise());
        show.setPerUserLimit(perUserLimit);
        show.setCreatedAt(OffsetDateTime.now());
        Show savedShow = showRepository.save(show);

        for( String seatNumber : request.seats()){
             Seat seat = new Seat();
             seat.setShow(savedShow);
             seat.setSeatNumber(seatNumber);
             seat.setStatus(SeatStatus.AVAILABLE);
             seat.setCreatedAt(OffsetDateTime.now());
             seatRepository.save(seat);
        }
        return savedShow;
    }
}
