# Booking API findings

Observed against https://restful-booker.herokuapp.com on **5 September 2026**.
Priorities reflect the Reservation Hub scenario, not proven financial loss or a
security exploit in this sandbox. The three data-integrity defects below are the
highest priority. Additional findings explain the other intentionally failing tests.
IDs from the earlier investigation are retained; gaps are intentional.

## Reproduction setup

Use **Bash (Git Bash on Windows), curl and jq**. These commands are not PowerShell
syntax. Each helper invocation prints the creation response and, when an ID is
returned, a fresh GET followed by a cleanup attempt. These short reproductions
do not recheck ownership before deletion; run promptly only against this sandbox
because resets can reuse IDs. The Java suite adds ownership checks to its cleanup.
Requests use only the published sandbox credentials and synthetic guest data.

```bash
BASE=https://restful-booker.herokuapp.com
VALID='{"firstname":"QA-Repro","lastname":"QA-Repro","totalprice":250,"depositpaid":true,"bookingdates":{"checkin":"2030-05-10","checkout":"2030-05-15"},"additionalneeds":"Breakfast"}'

create_and_read() {
  result=$(curl -sS --max-time 30 "$BASE/booking" \
    -H 'Content-Type: application/json' -H 'Accept: application/json' \
    --data "$1" -w '\n%{http_code}')
  printf '%s\n' "$result"
  body=$(printf '%s\n' "$result" | sed '$d')
  id=$(printf '%s' "$body" | jq -er '.bookingid // empty' 2>/dev/null) || return 0
  curl -i -sS --max-time 30 "$BASE/booking/$id" -H 'Accept: application/json'
  curl -i -sS --max-time 30 -u admin:password123 -X DELETE "$BASE/booking/$id"
}
```

<a id="BUG-02"></a>
## BUG-02
**Negative price is accepted and persisted — High.** Invalid monetary data can
produce incorrect invoices or settlement calculations; this is one half of the incident.

```bash
create_and_read "$(printf '%s' "$VALID" | jq '.totalprice = -1')"
```

**Expected:** HTTP 400, no booking created. **Actual:** HTTP 200, `bookingid`
returned; both the creation response and GET contain `totalprice: -1`.
PUT and PATCH also accept a negative price and mutate a previously valid booking.
The suite isolates each verb and asserts both rejection and unchanged stored state.

**Tests:** `invalidCreationIsRejected(negative price)` and
`invalidAmendmentLeavesBookingUnchanged(PUT/PATCH, negative price)` in
[ValidationTests.java](src/test/java/com/reservationhub/api/tests/ValidationTests.java).

<a id="BUG-03"></a>
## BUG-03
**Check-out before check-in is accepted — High.** An impossible stay can corrupt
occupancy calculations; this is the other half of the incident.

```bash
create_and_read "$(printf '%s' "$VALID" | jq '.bookingdates.checkout = "2030-05-02"')"
```

**Expected:** HTTP 400, no booking created. **Actual:** HTTP 200; GET confirms
check-in `2030-05-10` and check-out `2030-05-02`. PUT and PATCH also accept this
invalid ordering. Each test changes only the dates, keeping the price valid, so
the date defect cannot be hidden by price validation.

**Tests:** `invalidCreationIsRejected(reversed dates)` and
`invalidAmendmentLeavesBookingUnchanged(PUT/PATCH, reversed dates)` in
[ValidationTests.java](src/test/java/com/reservationhub/api/tests/ValidationTests.java).

<a id="BUG-06"></a>
## BUG-06
**Malformed and impossible dates are silently changed — High.** The guest's
arrival can be corrupted or moved without a validation error.

```bash
create_and_read "$(printf '%s' "$VALID" | jq '.bookingdates.checkin = "not-a-date"')"
create_and_read "$(printf '%s' "$VALID" | jq '.bookingdates.checkin = "2027-02-30"')"
```

**Expected:** HTTP 400 for both. **Actual:** HTTP 200; GET confirms
`not-a-date` becomes `0NaN-aN-aN`, and 30 February 2027 becomes `2027-03-02`.
No claim is made about the server's database implementation; persistence here
means that the value is retrievable through the API.

**Tests:** `invalidCreationIsRejected(malformed date/impossible date)` in
[ValidationTests.java](src/test/java/com/reservationhub/api/tests/ValidationTests.java).

## Additional findings

<a id="BUG-01"></a>
### BUG-01
**Failed login reports success — Medium.** Status-only clients cannot distinguish
a rejected login. This is not evidence of an authentication bypass: no token is issued.

```bash
curl -i "$BASE/auth" -H 'Content-Type: application/json' \
  --data '{"username":"admin","password":"wrong-password"}'
curl -i "$BASE/auth" -H 'Content-Type: application/json' --data '{}'
```

**Expected:** 401 for the wrong password; 400 or 401 for empty credentials.
**Actual:** both return 200 with `{"reason":"Bad credentials"}` and no token.
Test: `badLoginIsRejected` in [AuthTests.java](src/test/java/com/reservationhub/api/tests/AuthTests.java).

<a id="BUG-04"></a>
### BUG-04
**Missing required input returns a server error — Medium.** Partners receive
an internal-error response rather than actionable input validation.

```bash
create_and_read "$(printf '%s' "$VALID" | jq 'del(.firstname)')"
create_and_read '{}'
```

**Expected:** HTTP 400 identifying invalid input. **Actual:** HTTP 500, body
`Internal Server Error` (not an empty body).
Test: `invalidCreationIsRejected(missing firstname/empty object)` in
[ValidationTests.java](src/test/java/com/reservationhub/api/tests/ValidationTests.java).

<a id="BUG-05"></a>
### BUG-05
**Non-numeric price is converted to null — High.** Required pricing information
is lost even though the caller receives success.

```bash
create_and_read "$(printf '%s' "$VALID" | jq '.totalprice = "one hundred"')"
```

**Expected:** HTTP 400. **Actual:** HTTP 200; response and GET contain
`totalprice: null`. Test: `invalidCreationIsRejected(wrong price type)` in
[ValidationTests.java](src/test/java/com/reservationhub/api/tests/ValidationTests.java).

<a id="BUG-09"></a>
### BUG-09
**Absent bookings return 405 on write operations — Medium.** The response
describes an unsupported method rather than a missing resource.

```bash
id=$(curl -sS "$BASE/booking" -H 'Content-Type: application/json' \
  -H 'Accept: application/json' --data "$VALID" | jq -er '.bookingid')
curl -i -u admin:password123 -X DELETE "$BASE/booking/$id"
curl -i "$BASE/booking/$id"  # confirms 404 before the repro
curl -i -u admin:password123 -X PUT "$BASE/booking/$id" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' --data "$VALID"
curl -i -u admin:password123 -X PATCH "$BASE/booking/$id" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' --data '{"totalprice":300}'
curl -i -u admin:password123 -X DELETE "$BASE/booking/$id"
```

**Expected:** 404 for PUT/PATCH/DELETE after confirmed deletion.
**Actual:** 405 `Method Not Allowed` for all three, while GET returns 404.
Test: `absentBookingReturnsNotFound` in [BookingTests.java](src/test/java/com/reservationhub/api/tests/BookingTests.java).

<a id="BUG-11"></a>
### BUG-11
**An Accept list of supported formats returns 418 — Medium.** A normal
content-negotiation header prevents creation for affected clients.

```bash
curl -i "$BASE/booking" -H 'Content-Type: application/json' \
  -H 'Accept: application/json, application/xml' --data "$VALID"
create_and_read "$VALID"  # control: identical payload, single application/json Accept
```

**Expected:** HTTP 200 in one of the accepted formats. **Actual:** HTTP 418
`I'm a Teapot`; the single-JSON control succeeds. Only the tested header forms
are claimed, not every possible list. The main client uses literal
`application/json` so this defect does not block unrelated tests.
Test: `standardAcceptListIsSupported` in [ValidationTests.java](src/test/java/com/reservationhub/api/tests/ValidationTests.java).

<a id="BUG-12"></a>
### BUG-12
**Checkout filter reverses the documented comparison — Medium.** Search can
omit matching stays and return stays outside the documented lower bound.

The [API documentation](https://restful-booker.herokuapp.com/apidoc/index.html#api-Booking-GetBookings)
defines both date filters as **greater than or equal to** the supplied date.

```bash
id=$(curl -sS "$BASE/booking" -H 'Content-Type: application/json' \
  -H 'Accept: application/json' --data "$VALID" | jq -er '.bookingid')
curl -sS "$BASE/booking?checkout=2030-05-01" | jq --argjson id "$id" 'any(.[]; .bookingid == $id)'
curl -sS "$BASE/booking?checkout=2030-05-20" | jq --argjson id "$id" 'any(.[]; .bookingid == $id)'
curl -i "$BASE/booking/$id" -H 'Accept: application/json'
curl -i -u admin:password123 -X DELETE "$BASE/booking/$id"
```

**Expected:** `true`, then `false`, for a checkout of `2030-05-15`.
**Actual:** `false`, then `true`; GET confirms the original dates still exist.
Check-in behaves according to the documented lower bound. This is an observed
contract mismatch; product/API owners must decide whether implementation or docs
should change. Test: `dateFiltersRespectBounds` in
[BookingTests.java](src/test/java/com/reservationhub/api/tests/BookingTests.java).

## Deliberate boundaries

- The sandbox explicitly documents HTTP 201 for ping and deletion; tests follow
  that published contract rather than counting those quirks as new defects.
- Zero-priced stays are allowed as a stated product assumption (complimentary
  bookings); no same-day-stay policy was supplied, so none is invented.
- A disappearing fixture alone does not establish an auth defect on a shared
  service. Preserve its request/response evidence and investigate a reset or
  external modification before filing a bug.