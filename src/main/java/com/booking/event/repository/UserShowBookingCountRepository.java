package com.booking.event.repository;

import com.booking.event.entity.UserShowBookingCount;
import com.booking.event.entity.UserShowBookingCountId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserShowBookingCountRepository extends JpaRepository<UserShowBookingCount, UserShowBookingCountId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
          select u\s
          from UserShowBookingCount u
          where u.id.showId = :showId
          and u.id.userId = :userId
         \s""")
    Optional<UserShowBookingCount> findForUpdate(
            @Param("showId") Long showId,
            @Param("userId") String userId
    );

    @Modifying
    @Query(value = """
             INSERT INTO user_show_booking_counts(show_id , user_id , seat_count)
             VALUES (:showId, :userId , 0) 
             ON CONFLICT ( show_id , user_id) DO NOTHING                         
             """ , nativeQuery = true)
    void createIfAbsent(@Param("showId") Long showId,
                        @Param("userId") String userId);

}
