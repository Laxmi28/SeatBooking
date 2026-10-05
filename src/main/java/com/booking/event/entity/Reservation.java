package com.booking.event.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Reservation {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;
    @Column(name = "user_id" , nullable = false)
    private String userId;
    @Column(name = "amount_paise" , nullable = false)
    private Long amountPaise;
    @Column(name = "created_at" , nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "cancelled_at" , nullable = false)
    private OffsetDateTime cancelledAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

}
