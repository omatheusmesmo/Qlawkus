package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventList;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/calendar/v3")
@RegisterRestClient(baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
public interface GoogleCalendarRestClient {

    @GET
    @Path("/calendars/{calendarId}/events")
    CalendarEventList listEvents(
            @jakarta.ws.rs.PathParam("calendarId") String calendarId,
            @QueryParam("timeMin") String timeMin,
            @QueryParam("timeMax") String timeMax,
            @QueryParam("maxResults") Integer maxResults,
            @QueryParam("orderBy") String orderBy,
            @QueryParam("singleEvents") Boolean singleEvents);
}
