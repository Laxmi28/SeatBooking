package com.booking.event.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records" ,
   uniqueConstraints = {@UniqueConstraint(
           name = "uq_idempotency_key",
           columnNames = {
                   "show_id", "user_id", "idempotency_key"
           }
   )})
@NoArgsConstructor
@AllArgsConstructor
@Data
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "show_id" , nullable = false)
    private Long showId;
    @Column(name = "user_id" , nullable = false)
    private String userId;
    @Column(name = "idempotency_key" , nullable = false)
    private String idempotencyKey;
    @Column(name = "request_hash" , nullable = false)
    private String requestHash;
    @Column(name = "reservation_id" , nullable = false)
    private UUID reservationId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
