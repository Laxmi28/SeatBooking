# Concurrency Test

This directory contains a PowerShell script used to verify that the seat reservation API behaves correctly under concurrent requests for the same seat.

The test sends multiple reservation requests for the **same seat at the same time** and verifies that:

* Only one request successfully reserves the seat.
* All competing requests receive `409 Conflict`.
* No request results in a `5xx` server error.
* The database remains consistent after the concurrent requests.

---

## Prerequisites

* PowerShell 5.1+ or PowerShell 7+
* The application must be deployed and running.
* The deployed API must be publicly accessible.
* A show must exist with the seat used by the concurrency test.

---

## Test Script

The concurrency test is located at:

```text
scripts/concurrency-test.ps1
```

The script sends multiple simultaneous reservation requests against the same seat.

By default, the test uses:

```text
50 concurrent requests
```

The purpose is to create a **hot-seat race**, where many users attempt to reserve the same seat simultaneously.

---

## 1. Configure the Live URL

Open:

```text
scripts/concurrency-test.ps1
```

Set the application URL to your deployed Render service.

For example:

```powershell
$baseUrl = "https://YOUR-APP.onrender.com"
```

Do not include `/shows` at the end of the base URL if the script already constructs the endpoint.

---

## 2. Create a Test Show

Before running the concurrency test, create a show containing the seat that will be targeted.

Using Postman or PowerShell:

```http
POST https://YOUR-APP.onrender.com/shows
Content-Type: application/json
```

Request body:

```json
{
  "name": "Concurrency Test Show",
  "seats": [
    "A1",
    "A2",
    "A3",
    "A4",
    "A5"
  ],
  "pricePaise": 25000
}
```

Save the returned `showId`.

For example:

```text
showId = 10
```

Configure the same `showId` and target seat in the concurrency script.

---

## 3. Run the Test

From the project root:

```powershell
.\scripts\concurrency-test.ps1
```

You should see:

```text
Starting 50 concurrent reservation requests...
```

After all requests complete, the script prints a summary.

---

## 4. Expected Result

For 50 concurrent requests attempting to reserve the **same seat**, the expected result is:

```text
========== RESULTS ==========
Total requests : 50
201 Created    : 1
409 Conflict   : 49
5xx Errors     : 0
==============================
```

### What this proves

**1 successful request**

Only one request is allowed to transition the seat from:

```text
AVAILABLE → CONFIRMED
```

**49 conflicts**

The remaining requests correctly detect that the seat has already been reserved:

```text
AVAILABLE → CONFIRMED
       ↓
Other requests → 409 Conflict
```

**0 server errors**

Concurrency must not cause database locking failures or unexpected `500 Internal Server Error` responses.

---

## 5. Verify the Final Database State

After the concurrency test, verify the show:

```http
GET https://YOUR-APP.onrender.com/shows/{showId}
```

Example:

```json
{
  "showId": 10,
  "name": "Concurrency Test Show",
  "pricePaise": 25000,
  "totalSeats": 5,
  "availableSeats": 4,
  "heldSeats": 0,
  "confirmedSeats": 1,
  "seats": [
    {
      "seatNumber": "A1",
      "status": "CONFIRMED"
    },
    {
      "seatNumber": "A2",
      "status": "AVAILABLE"
    },
    {
      "seatNumber": "A3",
      "status": "AVAILABLE"
    },
    {
      "seatNumber": "A4",
      "status": "AVAILABLE"
    },
    {
      "seatNumber": "A5",
      "status": "AVAILABLE"
    }
  ]
}
```

The important invariant is:

```text
available + held + confirmed = total
```

For the example above:

```text
4 + 0 + 1 = 5
```

---

## 6. Why the Test Works

The reservation service uses database-level pessimistic locking when checking and updating a seat.

The important operation is effectively:

```text
SELECT seat FOR UPDATE
```

This ensures that concurrent transactions cannot simultaneously confirm the same seat.

The reservation operation is also transactional, so the following operations succeed or fail together:

```text
Lock seat
   ↓
Validate availability
   ↓
Create reservation
   ↓
Mark seat CONFIRMED
   ↓
Create reservation-seat mapping
   ↓
Update user's booking count
   ↓
Store idempotency record
```

If another request reaches the same seat concurrently, it waits for the database lock and then observes that the seat is already confirmed.

---

## 7. What Counts as a Failed Test?

The test should be investigated if you see any of the following:

### More than one `201`

Example:

```text
201 Created : 2
```

This indicates that the same seat may have been sold more than once.

### Any `5xx`

Example:

```text
5xx Errors : 3
```

The application should handle contention gracefully rather than returning server errors.

### Incorrect final seat state

For example:

```text
confirmedSeats = 2
```

when only one seat was targeted.

### Incorrect count reconciliation

The following must always hold:

```text
available + held + confirmed = total
```

---

## 8. Running Against Localhost

The same script can be used against the local application.

Change:

```powershell
$baseUrl = "https://YOUR-APP.onrender.com"
```

to:

```powershell
$baseUrl = "http://localhost:8080"
```

Start the application first, then run:

```powershell
.\scripts\concurrency-test.ps1
```

---

## 9. Running Against Render

For the final deployment test, use the public Render URL:

```powershell
$baseUrl = "https://YOUR-APP.onrender.com"
```

Then run:

```powershell
.\scripts\concurrency-test.ps1
```

This verifies the behavior against the actual deployed application and PostgreSQL database.

---

## Test Evidence

A successful final test should demonstrate:

```text
50 concurrent requests
        ↓
1 successful reservation
        ↓
49 rejected requests
        ↓
0 server errors
        ↓
1 confirmed seat
        ↓
Database counts remain consistent
```

This provides evidence that the reservation API is safe under concurrent access and prevents double-selling of seats.
