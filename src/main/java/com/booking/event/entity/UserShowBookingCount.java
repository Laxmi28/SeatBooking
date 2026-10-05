package com.booking.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_show_booking_counts")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserShowBookingCount {
    @EmbeddedId
    private UserShowBookingCountId id;
    @Column(name = "seat_count", nullable = false)
    private Integer seatCount = 0;

    public void increament( int seats){
        seatCount += seats;
    }
    public  void decreament( int seats){
        seatCount -=seats;
    }

}
