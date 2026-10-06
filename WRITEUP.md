# Seat Reservation Service — Deploy & Observe

## 1. Project Overview

This project implements a concurrent seat reservation service using Java, Spring Boot, PostgreSQL and Docker.

The primary goal was to design a reservation API that remains correct when multiple users attempt to reserve the same seat at the same time.

The service supports:

* Creating shows with assigned seats
* Reserving one or more seats
* Per-user booking limits
* Idempotency keys for safe retries
* Concurrent seat reservation
* Reservation cancellation
* Seat availability/status reporting
* Health endpoints
* Prometheus metrics
* Structured error responses
* Database migrations using Flyway

The implementation intentionally keeps the architecture simple. Instead of introducing Redis, Kafka or multiple microservices, concurrency correctness is handled at the PostgreSQL/database transaction level.

---

## 2. Technology Stack

* Java 17
* Spring Boot
* Spring Data JPA / Hibernate
* PostgreSQL 16
* Flyway
* Docker
* Maven
* Spring Boot Actuator
* Micrometer / Prometheus
* PowerShell concurrency test scripts

---

## 3. API Design

### Create a show

```http
POST /shows
```

Example request:

```json
{
  "name": "Concurrency Test Show",
  "seats": ["A1", "A2", "A3", "A4", "A5"],
  "pricePaise": 25000
}
```

Seats are initially created with status:

```text
AVAILABLE
```

Money is represented as integer paise instead of floating-point values.

---

### Reserve seats

```http
POST /shows/{showId}/reserve
```

Headers:

```text
X-User-Id: user-1
Idempotency-Key: unique-request-key
```

Request:

```json
{
  "seats": ["A1"]
}
```

Successful response:

```http
201 Created
```

Example:

```json
{
  "reservationId": "...",
  "showId": 6,
  "userId": "user-1",
  "seats": ["A1"],
  "amountPaise": 25000,
  "status": "CONFIRMED"
}
```

---

### Get show status

```http
GET /shows/{showId}
```

The response exposes:

* Total seats
* Available seats
* Held seats
* Confirmed seats
* Individual seat status

The implementation currently uses explicit cancellation rather than temporary holds, therefore:

```text
held = 0
```

The state invariant is:

```text
available + held + confirmed = total
```

---

### Cancel reservation

```http
DELETE /shows/reservations/{reservationId}
```

Cancellation changes:

```text
CONFIRMED → CANCELLED
```

and makes the associated seats available again:

```text
CONFIRMED → AVAILABLE
```

---

## 4. Database Design

The main tables are:

```text
shows
seats
reservations
reservation_seats
user_show_booking_counts
idempotency_records
```

### shows

Stores show-level information such as:

* show name
* ticket price
* per-user booking limit
* creation timestamp

### seats

Stores individual seats belonging to a show.

Important constraint:

```text
(show_id, seat_number) must be unique
```

This prevents duplicate seat definitions within a show.

### reservations

Stores the reservation itself, including:

* reservation ID
* show
* user
* total amount
* status
* creation/cancellation timestamps

### reservation_seats

Maps reservations to their seats.

A composite primary key is used:

```text
(reservation_id, seat_id)
```

### user_show_booking_counts

Stores the number of seats already booked by a user for a particular show.

Primary key:

```text
(show_id, user_id)
```

This allows the booking limit to be enforced safely under concurrent requests.

### idempotency_records

Stores:

* show ID
* user ID
* idempotency key
* request hash
* reservation ID

The request hash allows the service to distinguish:

```text
same idempotency key + same request
```

from:

```text
same idempotency key + different request
```

---

# 5. Concurrency Design

Concurrency correctness is the most important part of this project.

## 5.1 Seat locking

When reserving a seat, the service obtains a PostgreSQL row-level pessimistic write lock.

The repository uses:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
       SELECT s
       FROM Seat s
       WHERE s.id = :seatId
       """)
Optional<Seat> findByIdForUpdate(@Param("seatId") Long seatId);
```

Conceptually this results in a database operation equivalent to:

```sql
SELECT ...
FROM seats
WHERE id = ?
FOR UPDATE;
```

The transaction then checks:

```text
if seat.status != AVAILABLE
    reject reservation
else
    mark seat CONFIRMED
```

Because the row is locked, two concurrent transactions cannot both successfully confirm the same seat.

---

## 5.2 Why pessimistic write locking?

The requirement is correctness under a hot-seat concurrency scenario.

For example:

```text
User A ──┐
User B ──┤
User C ──┼──> Seat A1
User D ──┤
User E ──┘
```

Only one transaction should be allowed to make the state transition:

```text
AVAILABLE → CONFIRMED
```

A pessimistic write lock serializes access to that database row.

This avoids relying only on application-level checks such as:

```java
if (seat.isAvailable()) {
    seat.setConfirmed();
}
```

Without proper database locking, two transactions could both read `AVAILABLE` before either transaction commits.

---

# 6. Deterministic Lock Ordering

For multi-seat reservations, seats are locked in deterministic order based on their database IDs.

For example, if a request contains:

```text
A2, A1
```

the application first resolves the seats and sorts them by ID before acquiring locks.

This prevents two transactions from acquiring locks in opposite orders.

For example:

```text
Transaction 1:
A1 → A2

Transaction 2:
A2 → A1
```

could otherwise create a deadlock:

```text
T1 holds A1 and waits for A2
T2 holds A2 and waits for A1
```

Using a consistent ordering makes both transactions acquire locks in the same order.

---

# 7. Per-User Booking Limit

The default booking limit is:

```text
4 seats per user per show
```

The application maintains a row in:

```text
user_show_booking_counts
```

The row is also pessimistically locked before checking/updating the count.

The transaction performs:

```text
lock user's booking-count row
        ↓
read current count
        ↓
check requested seats
        ↓
reserve seats
        ↓
increment count
        ↓
commit
```

For example:

```text
Existing count = 3
Requested seats = 2
Limit = 4

3 + 2 > 4
       ↓
   reject request
```

This makes the limit concurrency-safe for simultaneous requests from the same user.

---

# 8. All-or-Nothing Reservation

The service uses an all-or-nothing reservation model.

For a request:

```json
{
  "seats": ["A1", "A2", "A3"]
}
```

either:

```text
A1 + A2 + A3 → CONFIRMED
```

or:

```text
A1 + A2 + A3 → none confirmed
```

If one requested seat is already unavailable, the entire transaction is rolled back.

This prevents partial reservations.

---

# 9. Transaction Boundaries

The reservation operation is executed inside a database transaction.

The major steps happen within the same transaction:

```text
Validate request
      ↓
Check idempotency
      ↓
Lock booking-count row
      ↓
Check user limit
      ↓
Resolve requested seats
      ↓
Lock seats
      ↓
Check seat availability
      ↓
Create reservation
      ↓
Mark seats CONFIRMED
      ↓
Create reservation-seat mappings
      ↓
Update booking count
      ↓
Commit
```

If an error occurs before commit, the transaction is rolled back.

This is important because the reservation involves multiple tables that must remain consistent.

---

# 10. Idempotency

The API accepts:

```text
Idempotency-Key
```

The purpose is to make retries safe.

For example, a client may successfully submit a reservation but fail to receive the response because of a network timeout.

The client retries using the same key:

```text
Idempotency-Key: abc-123
```

The service should return the original reservation rather than creating another reservation.

The idempotency record contains a hash of the original request.

Therefore:

```text
same key + same request
        ↓
return original reservation
```

while:

```text
same key + different request
        ↓
409 Conflict
```

This prevents accidental reuse of an idempotency key for a different operation.

---

# 11. Cancellation

The project uses explicit cancellation instead of time-based seat holds.

The lifecycle is:

```text
AVAILABLE
    ↓
CONFIRMED
    ↓
CANCELLED
    ↓
AVAILABLE
```

A cancelled reservation remains in the database for historical purposes, while the associated seats become available again.

The booking count is also decremented.

---

# 12. Error Handling

The application uses a global exception handler to convert application errors into meaningful HTTP responses.

Examples:

```text
400 BAD_REQUEST
```

for invalid input.

```text
404 NOT_FOUND
```

for a missing show, seat or reservation.

```text
409 CONFLICT
```

for business conflicts such as:

* Seat already taken
* Booking limit exceeded
* Attempting to cancel another user's reservation
* Reusing an idempotency key with a different request

The goal is to avoid returning `500 Internal Server Error` for expected business contention.

---

# 13. Concurrency Testing

A PowerShell script was created to generate concurrent reservation requests against the same seat.

The test sends:

```text
50 concurrent requests
```

from different users against the same seat.

Expected behavior:

```text
1 successful reservation
49 rejected reservations
0 server errors
```

### Actual test result

```text
========== RESULTS ==========
Total requests : 50
201 Created    : 1
409 Conflict   : 49
5xx Errors     : 0
==============================
```

This demonstrates that the hot-seat concurrency requirement is satisfied in the local test.

The result confirms:

* Exactly one request acquired the seat successfully.
* The remaining requests observed the seat as unavailable.
* Contention did not result in 500 errors.
* The same seat was not double-sold.

---

# 14. Observability

Spring Boot Actuator is used for application health and operational visibility.

The application exposes:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/prometheus
```

Prometheus metrics are provided using Micrometer.

Metrics include reservation outcomes and seat availability.

The purpose is to make the correctness of the reservation system observable rather than relying only on application logs.

---

# 15. Health Checks

The application uses Spring Boot health indicators and readiness/liveness probes.

The intended behavior is:

```text
Liveness
    ↓
Is the application process alive?

Readiness
    ↓
Can the application safely serve traffic?
    ↓
Database connectivity
```

A database failure should therefore prevent the service from being considered ready to receive traffic.

---

# 16. Database Migrations

Flyway is used to manage database schema changes.

The initial schema is created through:

```text
V1__create_schema.sql
```

Hibernate is configured with:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Therefore Hibernate validates the schema rather than creating or modifying production tables automatically.

This keeps schema ownership with Flyway.

---

# 17. Why PostgreSQL Instead of an In-Memory Lock?

An in-memory Java lock such as:

```java
synchronized
```

or:

```java
ReentrantLock
```

would only protect requests handled by the same JVM instance.

It would not provide correctness if the application were scaled to multiple instances:

```text
Instance 1 ──┐
              ├── PostgreSQL
Instance 2 ──┘
```

Database row locking provides a concurrency boundary shared by all application instances.

This makes PostgreSQL the source of truth for seat ownership.

---

# 18. Consistency vs Availability

For seat reservations, correctness is more important than accepting every request.

The system prioritizes:

```text
Consistency
    >
Availability during contention
```

It is acceptable for a request to receive:

```text
409 Conflict
```

when another user has already acquired the seat.

It is not acceptable for two users to both receive:

```text
201 Created
```

for the same seat.

Therefore the system deliberately uses database locking and transactions to protect the invariant.

---

# 19. AI / LLM Usage

LLM tools were used as an engineering assistant during development.

The LLM was used for:

* Discussing the system design
* Breaking the assignment into implementation steps
* Reviewing concurrency approaches
* Explaining PostgreSQL row locking
* Explaining pessimistic locking
* Identifying potential race conditions
* Reviewing repository and service design
* Helping debug Spring Boot and JPA errors
* Suggesting test scenarios
* Creating initial versions of test scripts
* Reviewing API behavior
* Improving error handling
* Explaining trade-offs between different approaches

The LLM was particularly useful for reasoning through concurrency scenarios such as:

```text
Request A → Seat A1
Request B → Seat A1
```

and multi-seat locking scenarios such as:

```text
Request A → A1, A2
Request B → A2, A1
```

The implementation was tested and adapted locally rather than treating generated code as automatically correct.

For example, development involved debugging issues including:

* JPA repository method definitions
* Flyway schema validation
* Missing reservation-seat persistence
* HTTP exception mapping
* PowerShell concurrency testing
* Concurrent seat reservation behavior

The LLM was therefore used as a development and reasoning tool, while the final implementation was validated through compilation, API testing, database inspection and concurrency testing.

---

# 20. Key Engineering Decisions

### PostgreSQL row locking

Chosen because seat ownership must be correct under concurrent requests and across potential application instances.

### Pessimistic write locking

Chosen because the primary requirement is preventing double-selling of a scarce resource.

### Deterministic lock ordering

Used to reduce the risk of deadlocks for multi-seat reservations.

### Database transactions

Used to keep reservation, seat status, reservation-seat mappings and booking counts consistent.

### Integer paise

Used instead of floating-point monetary values.

### Explicit cancellation

Chosen instead of implementing temporary holds and expiration to keep the solution focused and easier to reason about.

### No Redis/Kafka for core reservation correctness

The reservation workflow does not require additional distributed infrastructure. PostgreSQL already provides the transactional and locking guarantees required by the assignment.

---

# 21. Known Limitations / Future Improvements

Possible improvements for a production-scale system include:

1. Distributed rate limiting for abusive clients.
2. More comprehensive idempotency handling under simultaneous identical requests.
3. Per-show Prometheus seat gauges rather than a single aggregate gauge.
4. Authentication and authorization using a real identity provider.
5. Payment integration with an external payment service.
6. Reservation expiration if temporary holds are introduced.
7. More extensive load testing using tools such as k6 or Gatling.
8. Distributed tracing using OpenTelemetry.
9. More detailed structured logging and correlation IDs.
10. Deployment with multiple application instances to validate distributed concurrency behavior.
11. Automated CI/CD pipeline with integration and concurrency tests.
12. Database connection-pool and transaction tuning for higher traffic.

---

# 22. Final Summary

The main engineering challenge in this project is not creating the REST endpoints. It is maintaining correctness when multiple requests modify the same scarce resource concurrently.

The implemented design uses:

```text
Spring Boot
     ↓
Transactional Service
     ↓
PostgreSQL
     ↓
Row-level pessimistic locking
     ↓
Atomic seat state transition
```

The hot-seat concurrency test produced:

```text
50 requests
1 successful reservation
49 conflicts
0 server errors
```

which demonstrates the core no-double-sell behavior under concurrent access.

The project focuses on correctness, transactional consistency and observability while intentionally avoiding unnecessary distributed infrastructure.
