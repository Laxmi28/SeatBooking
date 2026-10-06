package com.booking.event.service;

import com.booking.event.dto.ReservationResponse;
import com.booking.event.dto.ReserveRequest;
import com.booking.event.entity.*;
import com.booking.event.exception.ConflictException;
import com.booking.event.exception.NotFoundException;
import com.booking.event.metrics.ReservationMetrics;
import com.booking.event.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final UserShowBookingCountRepository bookingCountRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ReservationMetrics reservationMetrics;
    private final ShowMetricsService showMetricsService;
    public ReservationService(
            ShowRepository showRepository,
            ShowMetricsService showMetricsService,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository,
            UserShowBookingCountRepository bookingCountRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            ReservationMetrics reservationMetrics) {
                this.reservationMetrics = reservationMetrics;
                this.showMetricsService = showMetricsService;
                this.showRepository = showRepository;
                this.seatRepository = seatRepository;
                this.reservationRepository = reservationRepository;
                this.reservationSeatRepository = reservationSeatRepository;
                this.bookingCountRepository = bookingCountRepository;
                this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public ReservationResponse reserve(Long showId,
                                       String userId,
                                       ReserveRequest request,
                                       String idempotencyKey){
        validateRequest(request,idempotencyKey);
        String requestHash = generateRequestHash(request);
        var existingRecord = idempotencyRecordRepository.findByShowIdAndUserIdAndIdempotencyKey(showId,userId,idempotencyKey);

        if( existingRecord.isPresent()){
            IdempotencyRecord record = existingRecord.get();

            if( !record.getRequestHash().equals(requestHash)){
                throw new ConflictException("Idempotency key was already used with a different request");
            }
            return buildReservationResponse(
                    record.getReservationId());
        }

        // not an existing record
        Show show = showRepository.findById(showId).orElseThrow(() ->new IllegalArgumentException("Show does not exists"));

         bookingCountRepository.createIfAbsent(showId,userId);

         UserShowBookingCount bookingCount = bookingCountRepository
                                             .findForUpdate(showId, userId)
                                             .orElseThrow(() -> new IllegalStateException("Unable to initialize booking count"));

         int requestedSeats = request.seats().size();

         if( bookingCount.getSeatCount() + requestedSeats  > show.getPerUserLimit()){
             reservationMetrics.bookingLimitExceeded();
             throw  new ConflictException("Per-user booking limit exceeded. Maximum allowed: " + show.getPerUserLimit());
         }


        List<Seat> requestedSeatsList = new ArrayList<>();

        for(String seatNumber : request.seats()){

            Seat seat = seatRepository.findByShowIdAndSeatNumber(showId,seatNumber)
                    .orElseThrow(() -> new NotFoundException("Seat not found : " + seatNumber));
            requestedSeatsList.add(seat);
        }
        //acquiring the Lock in deterministic order
        requestedSeatsList.sort(Comparator.comparing(Seat::getId));
        List<Seat> lockedSeats = new ArrayList<>();

         for( Seat seat : requestedSeatsList){

             Seat lockedSeat = seatRepository.findByIdForUpdate(seat.getId())
                     .orElseThrow(() -> new NotFoundException("Seat not found : " + seat.getSeatNumber()));

             if ( lockedSeat.getStatus() != SeatStatus.AVAILABLE){
                 reservationMetrics.seatTaken();
                 throw  new ConflictException("Seat already taken : " + seat.getSeatNumber());
             }

             lockedSeats.add(lockedSeat);


         }
        // make reservation for the locked seats

        UUID  reservationId= UUID.randomUUID();

        long amountPaise = (long) lockedSeats.size() * show.getPriceInPaise();

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setShow(show);
        reservation.setUserId(userId);
        reservation.setAmountPaise(amountPaise);
        reservation.setCreatedAt(OffsetDateTime.now());
        reservation.setStatus(ReservationStatus.CONFIRMED);

        reservationRepository.save(reservation);

        // make sure to mark the seat status as confirmed

        for(Seat s : lockedSeats){
            s.setStatus(SeatStatus.CONFIRMED);
        }

        seatRepository.saveAll(lockedSeats);

        // STEP 7: Create reservation-seat mappings
        List<ReservationSeat> reservationSeats = lockedSeats.stream()
                .map(seat -> {

                    ReservationSeat reservationSeat = new ReservationSeat();

                    reservationSeat.setReservationId(reservationId);
                    reservationSeat.setSeatId(seat.getId());

                    return reservationSeat;
                })
                .toList();

        reservationSeatRepository.saveAll(reservationSeats);

        bookingCount.increment(lockedSeats.size());
        bookingCountRepository.save(bookingCount);
        reservationMetrics.reservationConfirmed();
        showMetricsService.updateAvailableSeats(showId);



        return buildReservationResponse(reservationId);
    }

    private void validateRequest(ReserveRequest request,String idempotencyKey){

        if( request == null || request.seats() == null || request.seats().isEmpty()){
            throw  new IllegalArgumentException("At least one seat must be requested");
        }

        if(idempotencyKey == null || idempotencyKey.isBlank()){
            throw  new IllegalArgumentException("Idempotency key is required");
        }

        Set<String> uniqueSeats = new HashSet<>(request.seats());
        if(uniqueSeats.size() != request.seats().size()){
            throw new IllegalArgumentException("Duplicate seats are not allowed");
        }
    }
    private String generateRequestHash(ReserveRequest request) {

        String canonicalRequest = request.seats()
                .stream()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));

        try {

            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(canonicalRequest.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8));

            return java.util.HexFormat.of().formatHex(hash);

        } catch (java.security.NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "Unable to generate request hash", e);
        }
    }

    private ReservationResponse buildReservationResponse(UUID reservationId){
        // 1. find the reservation
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // 2. find all reservation-seat mappings
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservationId(reservationId);

        // 3.Extracting the seat IDs
        List<Long> seatIds = reservationSeats.stream().map(ReservationSeat::getSeatId).toList();

        // 4.fetching the actual seat entities
        List<Seat> seats = seatRepository.findAllById(seatIds);

        //5. Extract seat numbers
        List<String> seatNumbers = seats.stream().map(Seat::getSeatNumber).toList();

        // 6. building the api response

        return new ReservationResponse(reservation.getId(),
                reservation.getShow().getId(),
                reservation.getUserId(),
                seatNumbers,
                reservation.getAmountPaise(),
                reservation.getStatus().name());
    }

    @Transactional
    public void cancelReservation(UUID reservationId , String userId){

        Reservation reservation = reservationRepository.findById(reservationId)
                                  .orElseThrow(()-> new NotFoundException("Reservation not found: " + reservationId));

        if( !reservation.getUserId().equals(userId)){
            throw  new ConflictException("You cannot cancel another user's reservation");
        }

        if( reservation.getStatus() == ReservationStatus.CANCELLED){
            throw new ConflictException("Reservation is already cancelled");
        }

        // doing the actual reservation stuff

        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservationId(reservationId);

        for ( ReservationSeat reservationSeat : reservationSeats){
            Seat seat = seatRepository.findByIdForUpdate(reservationSeat.getSeatId())
                    .orElseThrow(() ->new NotFoundException("Seat not found: " + reservationSeat.getSeatId()));

            seat.setStatus(SeatStatus.AVAILABLE);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(OffsetDateTime.now());
        reservationRepository.save(reservation);

        UserShowBookingCount bookingCount = bookingCountRepository.findForUpdate(
                reservation.getShow().getId(),
                userId
                 ).orElseThrow(() -> new IllegalStateException("Booking count not found"));

        bookingCount.decrement(reservationSeats.size());

        bookingCountRepository.save(bookingCount);
        reservationMetrics.reservationCancelled();
        showMetricsService.updateAvailableSeats(reservation.getShow().getId());
    }

}
