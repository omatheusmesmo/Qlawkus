package dev.omatheusmesmo.qlawkus.it.messaging;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DiscordReadinessTest {

    @Test
    void showsWhenDiscordIsDisabled() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("checks.find { it.name == 'discord-gateway' }.status", equalTo("UP"))
                .body("checks.find { it.name == 'discord-gateway' }.data.status", equalTo("disabled"));
    }
}
