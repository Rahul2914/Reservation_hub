# Reservation Hub API safety net

Java 17 + REST Assured + TestNG + Allure. **23 executable cases, 12 test methods,
7 Java files.** The focus is the negative-price/bad-date incident, not test volume.

**Verified full run, 5 September 2026 at 15:16 IST (UTC+05:30):** 8 passed,
15 failed, 0 errors, 0 skipped. All 15 failed cases link to nine documented findings;
the failing test exit code is preserved, not suppressed.

## Run and read the results

From this project directory, set `JAVA_HOME` to JDK 17+. The Maven Wrapper downloads Maven. Run
`./mvnw clean test` (Windows: `.\mvnw.cmd clean test`), then
`./mvnw allure:report` even when tests fail. On Unix, use `sh ./mvnw` if the
wrapper does not yet have executable permission.

**Windows shortcut:** [run-tests.ps1](run-tests.ps1) does both and exports the
single-file report while preserving the test exit code: `.\run-tests.ps1`.
If Java reports a TLS trust-chain failure on this machine, use
`.\run-tests.ps1 -TrustStoreType Windows-ROOT`. Verification stays enabled;
the system trust store is not changed. Other machines normally need no override.

Open [report/index.html](report/index.html) directly in a browser: it embeds the
summary, feature groups, individual cases, assertions and HTTP request/response
attachments. See [BUGS.md](BUGS.md) for prioritized findings and curl reproductions.
On GitHub/GitLab, download the report and open it locally; the repository file view
may show HTML source rather than render it. This is the submission report; local
build results can later reflect individual test runs instead of the full suite.
For Unix single-file export after `allure:report`, run
`sh .allure/allure-2.29.0/bin/allure generate target/allure-results --clean --single-file -o report`.
Always start with `clean test` so earlier runs do not contaminate the report.

## Coverage against the assignment

| Requirement | Where / what is checked |
|---|---|
| Health, create → read → PUT → PATCH → delete | [BookingTests.java](src/test/java/com/reservationhub/api/tests/BookingTests.java): status, schemas, full body equality and persisted state; incomplete PUT cannot erase data |
| Authentication and both schemes | [AuthTests.java](src/test/java/com/reservationhub/api/tests/AuthTests.java): usable token; Basic-auth journey; missing/bad-cookie/bad-Basic rejection for all three write verbs, with unchanged state |
| List, all four filters, missing IDs | [BookingTests.java](src/test/java/com/reservationhub/api/tests/BookingTests.java): list schema, name inclusion/exclusion, date bounds, operations on an owned deleted ID |
| Incident and input boundaries | [ValidationTests.java](src/test/java/com/reservationhub/api/tests/ValidationTests.java): price and reversed dates isolated on POST/PUT/PATCH; missing field, empty object, wrong type, malformed/impossible dates; zero-price control in booking tests |
| Response contracts and reporting | Four JSON schemas; Allure feature labels and full HTTP evidence; generated report included |
| Bug report and short write-up | [BUGS.md](BUGS.md) and this README |

## How to understand and maintain it

Start with [BookingTests.java](src/test/java/com/reservationhub/api/tests/BookingTests.java):
arrange, act, assert response and stored state.
[TestBase.java](src/test/java/com/reservationhub/api/tests/TestBase.java) owns creation,
warm-up and cleanup. [ApiClient.java](src/test/java/com/reservationhub/api/client/ApiClient.java)
only sends requests. [BookingData.java](src/test/java/com/reservationhub/api/data/BookingData.java)
holds JSON maps and scenario tables. No token cache, custom retry listener,
dependency-ordered tests or AspectJ.
Override [config.properties](src/test/resources/config.properties) using JVM properties
or environment variables (e.g. `BASE_URL`); precedence is JVM → environment → file.

## Reliability, assumptions and exclusions

Tests run sequentially with unique, self-created fixtures and fresh tokens.
Cleanup runs after failures, verifies ownership before deletion (IDs can be reused
after a reset), and attaches warnings without masking assertions. Only startup
health checks are retried; **test writes and assertions are never replayed**.
A reset or another user can still remove a fixture mid-test: retain that failure
for investigation, not automatic bug classification. Without a returned ID or
ownership marker, safe cleanup is impossible; the sandbox reset is the fallback.

Zero is assumed valid for complimentary stays. Date regex schemas check format,
not calendar validity or ordering; dedicated negative cases check those rules.
Tests follow documented sandbox success codes (including 201 on DELETE/ping).
XML/form formats, load testing, exhaustive combinations and penetration testing
are excluded to prioritize incident prevention on a shared service. Reports contain
synthetic data and public sandbox credentials; redact before using real secrets.

**Submission:** publish this project, including the report and bug report, to the
requested Git repository and share its link within the assignment's two-day window.
Do not include the confidential assignment PDF. Nothing is committed or pushed automatically.