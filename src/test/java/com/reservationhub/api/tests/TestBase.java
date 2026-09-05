package com.reservationhub.api.tests;

import com.reservationhub.api.client.ApiClient;
import com.reservationhub.api.config.ApiConfig;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeSuite;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.testng.Assert.assertEquals;

/** Sequential suite: each test owns its fixtures; teardown runs even after an assertion fails. */
public abstract class TestBase {
    protected final ApiClient api = new ApiClient();
    private final Map<Integer, String> ownedBookings = new LinkedHashMap<>();

    @BeforeSuite(alwaysRun = true)
    public void waitForService() throws InterruptedException {
        String lastError = "No response";
        int attempts = ApiConfig.number("warmup.max.attempts");
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                int status = api.request().get("/ping").statusCode();
                if (status >= 200 && status < 300) return;
                lastError = "HTTP " + status;
            } catch (RuntimeException e) {
                lastError = e.toString();
            }
            if (attempt < attempts) Thread.sleep(ApiConfig.number("warmup.backoff.ms"));
        }
        throw new IllegalStateException("Environment unavailable after warm-up: " + lastError);
    }

    protected Response createTracked(Map<String, Object> booking) {
        Response response = api.create(booking);
        trackCreatedResponse(response, booking);
        return response;
    }

    protected void trackCreatedResponse(Response response, Map<String, Object> booking) {
        // Register before assertions, including when the server wrongly accepts invalid data.
        if (response.contentType() != null && response.contentType().contains("json")) {
            Integer id = response.jsonPath().get("bookingid");
            if (id != null) ownedBookings.put(id, (String) booking.get("lastname"));
        }
    }

    protected int createFixture(Map<String, Object> booking) {
        Response response = createTracked(booking);
        response.then().statusCode(200).body(matchesJsonSchemaInClasspath("schemas/created-booking.json"));
        assertEquals(response.jsonPath().getMap("booking"), booking, "Fixture must match submitted data");
        return response.jsonPath().getInt("bookingid");
    }

    protected void assertStored(int id, Map<String, Object> expected) {
        Response response = api.get(id);
        response.then().statusCode(200).body(matchesJsonSchemaInClasspath("schemas/booking.json"));
        assertEquals(response.jsonPath().getMap(""), expected, "Stored booking differs from expected state");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        for (var entry : ownedBookings.entrySet()) {
            try {
                Response current = api.get(entry.getKey());
                if (current.statusCode() == 404) continue; // Already deleted or sandbox reset.
                // A reset may reuse an ID. Never delete someone else's record.
                if (current.statusCode() != 200 || entry.getValue() == null
                        || !entry.getValue().equals(current.jsonPath().getString("lastname"))) {
                    Allure.addAttachment("Cleanup warning", "Ownership not confirmed for id " + entry.getKey());
                    continue;
                }
                int status = api.delete(entry.getKey(), api.basicAuth()).statusCode();
                if (status != 201 && status != 200 && status != 204 && status != 404) {
                    Allure.addAttachment("Cleanup warning", "DELETE " + entry.getKey() + " returned " + status);
                }
            } catch (RuntimeException e) {
                Allure.addAttachment("Cleanup warning", e.toString());
            }
        }
        ownedBookings.clear();
    }
}