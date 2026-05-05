package dev.omatheusmesmo.qlawkus.tools.google.sheets;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.SheetValues;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.UpdateValuesRequest;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.UpdateValuesResponse;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/v4/spreadsheets")
@RegisterRestClient(baseUri = "https://sheets.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleSheetsRestClient {

    @GET
    @Path("/{spreadsheetId}/values/{range}")
    SheetValues getValues(
            @PathParam("spreadsheetId") String spreadsheetId,
            @PathParam("range") String range);

    @PUT
    @Path("/{spreadsheetId}/values/{range}")
    UpdateValuesResponse updateValues(
            @PathParam("spreadsheetId") String spreadsheetId,
            @PathParam("range") String range,
            @QueryParam("valueInputOption") String valueInputOption,
            UpdateValuesRequest request);
}
