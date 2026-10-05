package com.booking.event.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table( name = "seats" ,
       uniqueConstraints =
        {
                 @UniqueConstraint(name = "uq_show_seat",
                 columnNames = {"show_id", "seat_number"})
        }
        )
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Seat {

    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id" , nullable = false)
    private Show show;
    @Column( name = "seat_number" , nullable = false)
    private String seatNumber ;
    @Enumerated(EnumType.STRING)
    @Column( nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;;
    @Column(name = "created_at" , nullable = false)
    private OffsetDateTime createdAt;

}
