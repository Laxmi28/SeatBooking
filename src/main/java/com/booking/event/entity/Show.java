package com.booking.event.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false , name = "price_paise")
    private Long priceInPaise;
    @Column(nullable = false , name = "per_user_limit")
    private Integer perUserLimit = 4;
    @Column(nullable = false , name = "created_at")
    private OffsetDateTime createdAt;
}
