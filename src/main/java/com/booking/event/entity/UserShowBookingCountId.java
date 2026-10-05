package com.booking.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserShowBookingCountId implements Serializable {

    @Column(name = "user_id")
    private String userId;
    @Column(name = "show_id")
    private Long showId;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserShowBookingCountId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(showId, that.showId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, showId);
    }
}
