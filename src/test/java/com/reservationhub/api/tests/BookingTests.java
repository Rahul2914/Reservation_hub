package com.reservationhub.api.tests;

import com.reservationhub.api.data.BookingData;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.time.LocalDate;
import java.util.Map;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.testng.Assert.assertEquals;

@Feature("Booking journeys and search")
public class BookingTests extends TestBase {
    @Test(groups = "health", description = "Health endpoint follows the documented sandbox contract")
    public void serviceIsHealthy() {
        api.request().get("/ping").then().statusCode(201).body(equalTo("Created"));
    }

    @Test(groups = "crud", description = "Create, read, PUT, PATCH and cancel with Basic authentication")
    public void completeBookingJourney() {
        Map<String, Object> booking = BookingData.valid();
        int id = createFixture(booking);
        assertStored(id, booking);

        // A full update with only one field must be rejected without losing existing fields.
        api.amend("PUT", id, Map.of("firstname", "Incomplete"), api.basicAuth()).then().statusCode(400);
        assertStored(id, booking);

        booking.put("firstname", "Updated guest");
        booking.put("totalprice", 430);
        booking.put("depositpaid", false);
        booking.put("bookingdates", BookingData.dates(LocalDate.now().plusDays(40), LocalDate.now().plusDays(45)));
        booking.put("additionalneeds", "Late arrival");
        Response put = api.amend("PUT", id, booking, api.basicAuth());
        put.then().statusCode(200).body(matchesJsonSchemaInClasspath("schemas/booking.json"));
        assertEquals(put.jsonPath().getMap(""), booking, "PUT response must match replacement");
        assertStored(id, booking);

        Response patch = api.amend("PATCH", id, Map.of("additionalneeds", "Cot"), api.basicAuth());
        booking.put("additionalneeds", "Cot");
        patch.then().statusCode(200).body(matchesJsonSchemaInClasspath("schemas/booking.json"));
        assertEquals(patch.jsonPath().getMap(""), booking, "PATCH must preserve unspecified fields");
        assertStored(id, booking);

        // 201 is unusual for DELETE but explicitly documented by this sandbox.
        api.delete(id, api.basicAuth()).then().statusCode(201).body(equalTo("Created"));
        api.get(id).then().statusCode(404).body(equalTo("Not Found"));
    }

    @Test(groups = "boundary", description = "Zero price is preserved for a complimentary stay")
    public void zeroPriceIsPreserved() {
        Map<String, Object> booking = BookingData.valid();
        booking.put("totalprice", 0);
        int id = createFixture(booking);
        assertStored(id, booking);
    }

    @Test(groups = "search", description = "List schema and guest-name filters find only matching fixtures")
    public void listAndNameFilters() {
        Map<String, Object> booking = BookingData.valid();
        int id = createFixture(booking);
        api.list(Map.of()).then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/booking-id-list.json"))
                .body("bookingid", hasItem(id));
        for (String field : new String[]{"firstname", "lastname"}) {
            api.list(Map.of(field, booking.get(field))).then().statusCode(200)
                    .body("bookingid", equalTo(java.util.List.of(id)));
        }
        api.list(Map.of("firstname", booking.get("firstname"), "lastname", "no-" + booking.get("lastname")))
                .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test(groups = {"search", "known-defect"}, description = "Date filters must follow the documented greater-than-or-equal bounds")
    @Issue("BUG-12")
    public void dateFiltersRespectBounds() {
        Map<String, Object> booking = BookingData.valid();
        int id = createFixture(booking);
        // Docs specify >= for BOTH filters, not an enclosing arrival/departure interval.
        SoftAssert check = new SoftAssert();
        for (String field : new String[]{"checkin", "checkout"}) {
            Response included = api.list(Map.of(field, LocalDate.now().plusDays(29).toString()));
            Response excluded = api.list(Map.of(field, LocalDate.now().plusDays(50).toString()));
            check.assertEquals(included.statusCode(), 200, field + " lower bound response");
            check.assertEquals(excluded.statusCode(), 200, field + " upper bound response");
            check.assertTrue(included.jsonPath().getList("bookingid").contains(id), field + " must include later stay");
            check.assertFalse(excluded.jsonPath().getList("bookingid").contains(id), field + " must exclude earlier stay");
        }
        assertStored(id, booking); // Rule out disappearance as the explanation for missing search results.
        check.assertAll();
    }

    @Test(groups = {"negative", "known-defect"}, description = "An absent booking is reported as not found on every verb")
    @Issue("BUG-09")
    public void absentBookingReturnsNotFound() {
        // Establish absence using our own deleted fixture, not a guessed global ID.
        Map<String, Object> booking = BookingData.valid();
        int id = createFixture(booking);
        api.delete(id, api.basicAuth()).then().statusCode(201);
        api.get(id).then().statusCode(404);
        SoftAssert check = new SoftAssert();
        check.assertEquals(api.amend("PUT", id, booking, api.basicAuth()).statusCode(), 404, "PUT absent id");
        check.assertEquals(api.amend("PATCH", id, Map.of("totalprice", 300), api.basicAuth()).statusCode(), 404, "PATCH absent id");
        check.assertEquals(api.delete(id, api.basicAuth()).statusCode(), 404, "DELETE absent id");
        check.assertAll();
    }
}