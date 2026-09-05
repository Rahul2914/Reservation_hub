package com.reservationhub.api.tests;

import com.reservationhub.api.data.BookingData;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;

@Feature("Input validation and incident prevention")
public class ValidationTests extends TestBase {
    @Test(groups = {"negative", "known-defect"}, dataProvider = "invalidBookings",
            dataProviderClass = BookingData.class, description = "Invalid booking input is rejected, not silently stored")
    public void invalidCreationIsRejected(String scenario, String bug) {
        Allure.issue(bug, "../BUGS.md#" + bug.toLowerCase());
        Response response = createTracked(BookingData.invalid(scenario));
        // Capture persisted state even though the status assertion will fail.
        if (response.statusCode() == 200) {
            Integer id = response.jsonPath().get("bookingid");
            if (id != null) api.get(id);
        }
        assertEquals(response.statusCode(), 400, scenario + ": expected rejection; response=" + response.asString());
    }

    @Test(groups = {"negative", "known-defect"}, dataProvider = "invalidAmendments",
            dataProviderClass = BookingData.class, description = "PUT and PATCH must not introduce the price/date incident")
    public void invalidAmendmentLeavesBookingUnchanged(String method, String scenario, String bug) {
        Allure.issue(bug, "../BUGS.md#" + bug.toLowerCase());
        Map<String, Object> original = BookingData.valid();
        int id = createFixture(original);
        String field = scenario.equals("negative price") ? "totalprice" : "bookingdates";
        Object invalidValue = BookingData.invalid(scenario).get(field);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (method.equals("PUT")) changes.putAll(original);
        changes.put(field, invalidValue);

        Response response = api.amend(method, id, changes, api.basicAuth());
        Response stored = api.get(id);
        SoftAssert check = new SoftAssert();
        check.assertEquals(response.statusCode(), 400, method + " must reject " + scenario);
        check.assertEquals(stored.statusCode(), 200, "Original booking must still exist");
        if (stored.statusCode() == 200) {
            check.assertEquals(stored.jsonPath().getMap(""), original, "Rejected update must not change stored data");
        }
        check.assertAll();
    }

    @Test(groups = {"contract", "known-defect"}, description = "A JSON-capable Accept list should allow creation")
    @Issue("BUG-11")
    public void standardAcceptListIsSupported() {
        Map<String, Object> booking = BookingData.valid();
        Response response = api.request().accept("application/json, application/xml")
                .body(booking).post("/booking");
        // Keep cleanup registration centralized even for custom request headers.
        trackCreatedResponse(response, booking);
        assertEquals(response.statusCode(), 200, "Both requested media types are supported by the API");
    }
}