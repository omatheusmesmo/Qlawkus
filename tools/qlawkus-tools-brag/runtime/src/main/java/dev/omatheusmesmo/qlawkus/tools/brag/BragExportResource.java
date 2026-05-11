package dev.omatheusmesmo.qlawkus.tools.brag;

import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Path("/api/brag")
@Authenticated
public class BragExportResource {

    @Inject
    @ClawTool
    BragTool bragTool;

    @GET
    @Path("/export")
    @Produces("text/markdown")
    public String export(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate) {
        return bragTool.generateMarkdownReport(startDate, endDate);
    }
}
