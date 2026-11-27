package dev.omatheusmesmo.qlawkus.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PermissionAdminResourceTest {

    private static final String BASE = "/api/admin/permissions";

    private static RequestSpecification owner() {
        return given().auth().basic("qlawkus", "qlawkus-test");
    }

    @AfterEach
    void reset() {
        owner().when().post(BASE + "/reset").then().statusCode(200);
    }

    @Test
    void everyPermissionEndpointRefusesAnonymousCallers() {
        given().when().get(BASE).then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"level\":\"ask\"}").when()
                .put(BASE + "/cells/shell/execute").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"level\":\"ask\"}").when()
                .put(BASE + "/tools/readFile").then().statusCode(401);
        given().when().post(BASE + "/presets/readonly").then().statusCode(401);
        given().when().post(BASE + "/reset").then().statusCode(401);
        given().when().get(BASE + "/explain/readFile").then().statusCode(401);
        given().when().get(BASE + "/audit").then().statusCode(401);
    }

    @Test
    void theMatrixListsEveryGroupWithAllSixOperations() {
        owner().when().get(BASE).then().statusCode(200)
                .body("operations", equalTo(List.of("read", "create", "update", "delete", "send", "execute")))
                .body("presets", hasItem("ask-writes"))
                .body("groups.group", hasItem("shell"))
                .body("groups.find { it.group == 'shell' }.cells.read.level", equalTo("allow"))
                .body("defaults.cells.delete.level", equalTo("allow"));
    }

    @Test
    void theAuthenticatedAdminLoosensAndTightensACellDirectly() {
        owner().contentType(ContentType.JSON).body("{\"level\":\"deny\"}").when()
                .put(BASE + "/cells/shell/read").then().statusCode(200).body("status", equalTo("APPLIED"));
        owner().when().get(BASE).then()
                .body("groups.find { it.group == 'shell' }.cells.read.level", equalTo("deny"));

        owner().contentType(ContentType.JSON).body("{\"level\":\"allow\"}").when()
                .put(BASE + "/cells/shell/read").then().statusCode(200).body("status", equalTo("APPLIED"));
        owner().when().get(BASE).then()
                .body("groups.find { it.group == 'shell' }.cells.read.level", equalTo("allow"));
    }

    @Test
    void aBadGroupOperationOrLevelIsRejectedAtTheBoundary() {
        owner().contentType(ContentType.JSON).body("{\"level\":\"ask\"}").when()
                .put(BASE + "/cells/nonesuch/read").then().statusCode(400).body("error", containsString("group"));
        owner().contentType(ContentType.JSON).body("{\"level\":\"ask\"}").when()
                .put(BASE + "/cells/shell/remove").then().statusCode(400);
        owner().contentType(ContentType.JSON).body("{\"level\":\"maybe\"}").when()
                .put(BASE + "/cells/shell/read").then().statusCode(400);
        owner().contentType(ContentType.JSON).body("{}").when()
                .put(BASE + "/cells/shell/read").then().statusCode(400);
    }

    @Test
    void aToolExceptionAndAPresetCanBeSetAndTheOutcomeIsReadBack() {
        owner().contentType(ContentType.JSON).body("{\"level\":\"ask\"}").when()
                .put(BASE + "/tools/readFile").then().statusCode(200);
        owner().when().get(BASE + "/explain/readFile").then().statusCode(200)
                .body("level", equalTo("ask"))
                .body("tool", equalTo("readFile"));

        owner().when().post(BASE + "/presets/readonly").then().statusCode(200).body("status", equalTo("APPLIED"));
        owner().when().get(BASE).then().body("preset", equalTo("readonly"))
                .body("defaults.cells.delete.level", equalTo("deny"));
        owner().when().post(BASE + "/presets/paranoid").then().statusCode(400);
    }

    @Test
    void explainGivesTheClassTheGroupAndTheReasons() {
        owner().when().get(BASE + "/explain/readFile").then().statusCode(200)
                .body("accessClass", equalTo("READ"))
                .body("group", equalTo("shell"))
                .body("reasons", hasSize(greaterThan(0)));
    }

    @Test
    void everyChangeLandsInTheAuditLogWithTheAuthenticatedUser() {
        owner().contentType(ContentType.JSON).body("{\"level\":\"ask\"}").when()
                .put(BASE + "/cells/shell/execute").then().statusCode(200);

        owner().when().get(BASE + "/audit").then().statusCode(200)
                .body("[0].target", equalTo("shell.execute"))
                .body("[0].actor", containsString("qlawkus"))
                .body("[0].channel", equalTo("rest"))
                .body("[0].before", equalTo("allow"))
                .body("[0].after", equalTo("ask"));
    }
}
