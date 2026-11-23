package dev.omatheusmesmo.qlawkus.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApprovalAdminResourceTest {

    private static final String UNKNOWN = "ffffffffffffffffffffffffffffffff";

    @Test
    void everyApprovalEndpointRefusesAnonymousCallers() {
        given().when().get("/api/admin/approvals").then().statusCode(401);
        given().when().get("/api/admin/approvals/" + UNKNOWN).then().statusCode(401);
        given().when().post("/api/admin/approvals/" + UNKNOWN + "/approve").then().statusCode(401);
        given().when().post("/api/admin/approvals/" + UNKNOWN + "/deny").then().statusCode(401);
    }

    @Test
    void listReturnsAnArrayForTheOwner() {
        given()
            .auth().basic("qlawkus", "qlawkus-test")
            .when()
            .get("/api/admin/approvals")
            .then()
            .statusCode(200)
            .body("$", hasSize(greaterThanOrEqualTo(0)));
    }

    @Test
    void aMalformedIdIsRejectedAtTheApiBoundary() {
        given()
            .auth().basic("qlawkus", "qlawkus-test")
            .when()
            .get("/api/admin/approvals/..%2F..%2Fetc")
            .then()
            .statusCode(400);
    }

    @Test
    void anUnknownIdIsNotFound() {
        given()
            .auth().basic("qlawkus", "qlawkus-test")
            .when()
            .post("/api/admin/approvals/" + UNKNOWN + "/approve")
            .then()
            .statusCode(404);
    }

    @Test
    void denyOfAnUnknownIdIsNotFound() {
        given()
            .auth().basic("qlawkus", "qlawkus-test")
            .when()
            .post("/api/admin/approvals/" + UNKNOWN + "/deny")
            .then()
            .statusCode(404);
    }
}
