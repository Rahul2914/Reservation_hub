package com.reservationhub.api.data;

import org.testng.annotations.DataProvider;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Maps mirror the JSON and also let negative tests send deliberately wrong types. */
public final class BookingData {
    private BookingData() { }

    public static Map<String, Object> valid() {
        String suffix = UUID.randomUUID().toString();
        Map<String, Object> booking = new LinkedHashMap<>();
        booking.put("firstname", "QA-" + suffix);
        booking.put("lastname", "Guest-" + suffix);
        booking.put("totalprice", 250);
        booking.put("depositpaid", true);
        booking.put("bookingdates", dates(LocalDate.now().plusDays(30), LocalDate.now().plusDays(34)));
        booking.put("additionalneeds", "Breakfast");
        return booking;
    }

    public static Map<String, String> dates(LocalDate checkin, LocalDate checkout) {
        return Map.of("checkin", checkin.toString(), "checkout", checkout.toString());
    }

    public static Map<String, Object> invalid(String scenario) {
        Map<String, Object> booking = valid();
        switch (scenario) {
            case "negative price" -> booking.put("totalprice", -1);
            case "reversed dates" -> booking.put("bookingdates",
                    dates(LocalDate.now().plusDays(34), LocalDate.now().plusDays(30)));
            case "missing firstname" -> booking.remove("firstname");
            case "empty object" -> booking.clear();
            case "wrong price type" -> booking.put("totalprice", "one hundred");
            case "malformed date" -> booking.put("bookingdates",
                    Map.of("checkin", "not-a-date", "checkout", "2027-03-05"));
            case "impossible date" -> booking.put("bookingdates",
                    Map.of("checkin", "2027-02-30", "checkout", "2027-03-05"));
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
        return booking;
    }

    @DataProvider(name = "invalidBookings")
    public static Object[][] invalidBookings() {
        return new Object[][] {
                {"negative price", "BUG-02"}, {"reversed dates", "BUG-03"},
                {"missing firstname", "BUG-04"}, {"empty object", "BUG-04"},
                {"wrong price type", "BUG-05"}, {"malformed date", "BUG-06"},
                {"impossible date", "BUG-06"}
        };
    }

    @DataProvider(name = "invalidAmendments")
    public static Object[][] invalidAmendments() {
        return new Object[][] {
                {"PUT", "negative price", "BUG-02"}, {"PATCH", "negative price", "BUG-02"},
                {"PUT", "reversed dates", "BUG-03"}, {"PATCH", "reversed dates", "BUG-03"}
        };
    }

    @DataProvider(name = "badAuth")
    public static Object[][] badAuth() {
        return new Object[][] {{"missing"}, {"invalid cookie"}, {"invalid basic"}};
    }
}