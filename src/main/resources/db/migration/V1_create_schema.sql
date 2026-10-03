-- ============================================================
-- SHOWS
-- ============================================================

CREATE TABLE shows (
id BIGSERIAL PRIMARY KEY,
name VARCHAR(255) NOT NULL,
price_paise BIGINT NOT NULL,
per_user_limit INTEGER NOT NULL DEFAULT 4,
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

CONSTRAINT chk_show_price_positive
    CHECK (price_paise >= 0),

CONSTRAINT chk_show_user_limit_positive
    CHECK (per_user_limit > 0)
);


-- ============================================================
-- SEATS
-- ============================================================

CREATE TABLE seats (
id BIGSERIAL PRIMARY KEY,
show_id BIGINT NOT NULL,
seat_number VARCHAR(50) NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

CONSTRAINT fk_seat_show
    FOREIGN KEY (show_id)
 REFERENCES shows(id)
 ON DELETE CASCADE,

CONSTRAINT uq_show_seat
    UNIQUE (show_id, seat_number),

CONSTRAINT chk_seat_status
    CHECK (status IN ('AVAILABLE', 'CONFIRMED'))
);

CREATE INDEX idx_seats_show_id
    ON seats(show_id);


-- ============================================================
-- RESERVATIONS
-- ============================================================

CREATE TABLE reservations (
id UUID PRIMARY KEY,
show_id BIGINT NOT NULL,
user_id VARCHAR(255) NOT NULL,
amount_paise BIGINT NOT NULL,
status VARCHAR(20) NOT NULL,
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
cancelled_at TIMESTAMP WITH TIME ZONE,

CONSTRAINT fk_reservation_show
FOREIGN KEY (show_id)
 REFERENCES shows(id),

CONSTRAINT chk_reservation_amount
CHECK (amount_paise >= 0),

CONSTRAINT chk_reservation_status
CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_reservations_show_id
    ON reservations(show_id);

CREATE INDEX idx_reservations_user_show
    ON reservations(user_id, show_id);


-- ============================================================
-- RESERVATION SEATS
-- ============================================================

CREATE TABLE reservation_seats (
 reservation_id UUID NOT NULL,
 seat_id BIGINT NOT NULL,

 PRIMARY KEY (reservation_id, seat_id),

 CONSTRAINT fk_reservation_seats_reservation
  FOREIGN KEY (reservation_id)
      REFERENCES reservations(id)
      ON DELETE CASCADE,

 CONSTRAINT fk_reservation_seats_seat
  FOREIGN KEY (seat_id)
      REFERENCES seats(id)
);


-- ============================================================
-- USER + SHOW BOOKING COUNT
-- ============================================================

CREATE TABLE user_show_booking_counts (
     show_id BIGINT NOT NULL,
     user_id VARCHAR(255) NOT NULL,
     seat_count INTEGER NOT NULL DEFAULT 0,

     PRIMARY KEY (show_id, user_id),

     CONSTRAINT fk_booking_count_show
         FOREIGN KEY (show_id)
             REFERENCES shows(id)
             ON DELETE CASCADE,

     CONSTRAINT chk_booking_count_non_negative
         CHECK (seat_count >= 0)
);


-- ============================================================
-- IDEMPOTENCY
-- ============================================================

CREATE TABLE idempotency_records (
id BIGSERIAL PRIMARY KEY,

show_id BIGINT NOT NULL,
user_id VARCHAR(255) NOT NULL,
idempotency_key VARCHAR(255) NOT NULL,

request_hash VARCHAR(64) NOT NULL,

reservation_id UUID NOT NULL,

created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

CONSTRAINT fk_idempotency_show
    FOREIGN KEY (show_id)
        REFERENCES shows(id)
        ON DELETE CASCADE,

CONSTRAINT fk_idempotency_reservation
    FOREIGN KEY (reservation_id)
        REFERENCES reservations(id),

CONSTRAINT uq_idempotency_key
    UNIQUE (show_id, user_id, idempotency_key)
);

CREATE INDEX idx_idempotency_reservation
    ON idempotency_records(reservation_id);