package com.reservationhub.api.client;

import com.reservationhub.api.config.ApiConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** HTTP plumbing only. Assertions belong in tests, not in this client. */
public final class ApiClient {
    static {
        // Optional OS trust store; certificate verification is never disabled.
        String trustStore = ApiConfig.get("ssl.truststore.type");
        if (!trustStore.isBlank()) System.setProperty("javax.net.ssl.trustStoreType", trustStore);
    }

    public RequestSpecification request() {
        return given().baseUri(ApiConfig.get("base.url"))
                .contentType("application/json")
                // REST Assured's JSON enum expands Accept to a list; BUG-11 isolates that defect.
                .accept("application/json")
                .config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", ApiConfig.number("http.connect.timeout.ms"))
                        .setParam("http.socket.timeout", ApiConfig.number("http.socket.timeout.ms"))))
                .filter(new AllureRestAssured());
    }

    public RequestSpecification basicAuth() {
        return request().auth().preemptive().basic(ApiConfig.get("auth.username"), ApiConfig.get("auth.password"));
    }

    public Response login(String username, String password) {
        return request().body(Map.of("username", username, "password", password)).post("/auth");
    }

    public Response create(Map<String, Object> booking) {
        return request().body(booking).post("/booking");
    }

    public Response get(int id) {
        return request().get("/booking/{id}", id);
    }

    public Response list(Map<String, ?> filters) {
        return request().queryParams(filters).get("/booking");
    }

    public Response amend(String method, int id, Map<String, Object> booking, RequestSpecification auth) {
        return auth.body(booking).request(method, "/booking/{id}", id);
    }

    public Response delete(int id, RequestSpecification auth) {
        return auth.delete("/booking/{id}", id);
    }
}