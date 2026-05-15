package dev.omatheusmesmo.qlawkus.it.review;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.review.CodeReviewTool;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/api/test/review")
@Authenticated
public class CodeReviewTestResource {

    @Inject
    @ClawTool
    CodeReviewTool codeReviewTool;

    @GET
    @Path("/run")
    public Response runLocalTests(@QueryParam("cmd") String cmd) {
        CommandResult result = codeReviewTool.runLocalTests(cmd);
        return Response.ok(result).build();
    }
}
