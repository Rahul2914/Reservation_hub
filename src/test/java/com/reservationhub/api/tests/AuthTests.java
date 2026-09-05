package com.reservationhub.api.tests;

import com.reservationhub.api.config.ApiConfig;
import com.reservationhub.api.data.BookingData;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.Map;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;

@Feature("Authentication")
public class AuthTests extends TestBase {
    @Test(groups = "auth", description = "A returned token authorizes PUT, PATCH and DELETE")
    public void tokenAuthorizesBookingChanges() {
        Response login = api.login(ApiConfig.get("auth.username"), ApiConfig.get("auth.password"));
        login.then().statusCode(200).body(matchesJsonSchemaInClasspath("schemas/auth-token.json"));
        String token = login.jsonPath().getString("token");
        Map<String, Object> booking = BookingData.valid();
        int id = createFixture(booking);

        booking.put("totalprice", 300);
        api.amend("PUT", id, booking, api.request().cookie("token", token)).then().statusCode(200);
        assertStored(id, booking);
        api.amend("PATCH", id, Map.of("depositpaid", false), api.request().cookie("token", token))
                .then().statusCode(200).body("depositpaid", equalTo(false));
        booking.put("depositpaid", false);
        assertStored(id, booking);
        api.delete(id, api.request().cookie("token", token)).then().statusCode(201);
        api.get(id).then().statusCode(404);
    }

    @Test(groups = {"auth", "known-defect"}, description = "Invalid and empty credentials must not report success")
    @Issue("BUG-01")
    public void badLoginIsRejected() {
        Response wrong = api.login(ApiConfig.get("auth.username"), "wrong-password");
        Response empty = api.request().body(Map.of()).post("/auth");
        SoftAssert check = new SoftAssert();
        check.assertEquals(wrong.statusCode(), 401, "Wrong password: expected 401");
        check.assertTrue(empty.statusCode() == 400 || empty.statusCode() == 401, "Empty credentials: expected 400/401");
        check.assertNull(wrong.jsonPath().get("token"), "Invalid credentials must never issue a token");
        check.assertNull(empty.jsonPath().get("token"), "Empty credentials must never issue a token");
        check.assertAll();
    }

    @Test(groups = "auth", dataProvider = "badAuth", dataProviderClass = BookingData.class,
            description = "Rejected PUT, PATCH and DELETE must leave the booking unchanged")
    public void writesRequireValidCredentials(String authMode) {
        Map<String, Object> original = BookingData.valid();
        int id = createFixture(original);
        Map<String, Object> replacement = new java.util.LinkedHashMap<>(original);
        replacement.put("totalprice", 800);
        SoftAssert check = new SoftAssert();
        for (String method : new String[]{"PUT", "PATCH", "DELETE"}) {
            RequestSpecification request = api.request();
            if (authMode.equals("invalid cookie")) request.cookie("token", "not-a-valid-token");
            if (authMode.equals("invalid basic")) request.auth().preemptive().basic("admin", "wrong-password");
            Response response = method.equals("DELETE") ? api.delete(id, request)
                    : api.amend(method, id, replacement, request);
            check.assertEquals(response.statusCode(), 403, method + " with " + authMode);
            Response stored = api.get(id);
            check.assertEquals(stored.statusCode(), 200, "Booking must survive rejected " + method);
            if (stored.statusCode() == 200) {
                check.assertEquals(stored.jsonPath().getMap(""), original, "No mutation after rejected " + method);
            }
        }
        check.assertAll();
    }
}