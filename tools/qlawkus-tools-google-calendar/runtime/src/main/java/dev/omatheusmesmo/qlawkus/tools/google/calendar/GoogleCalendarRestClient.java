package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEvent;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventList;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyRequest;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyResponse;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.time.temporal.ChronoUnit;

/**
 * {@code @Retry} covers one API call, not one tool operation - a tool that calls this client three
 * times must not multiply into nine attempts. Only {@link dev.omatheusmesmo.qlawkus.http.
 * TransientHttpException} (429, 5xx) and {@code ProcessingException} (network-level failure) are
 * retried; every other status - 401 will never pass, for instance - propagates immediately.
 */
@Path("/calendar/v3")
@RegisterRestClient(configKey = "google-calendar", baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
@Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
        jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
        retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
@ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
public interface GoogleCalendarRestClient {

    @GET
    @Path("/calendars/{calendarId}/events")
    CalendarEventList listEvents(
            @PathParam("calendarId") String calendarId,
            @QueryParam("timeMin") String timeMin,
            @QueryParam("timeMax") String timeMax,
            @QueryParam("maxResults") Integer maxResults,
            @QueryParam("orderBy") String orderBy,
            @QueryParam("singleEvents") Boolean singleEvents);

    @POST
    @Path("/calendars/{calendarId}/events")
    CalendarEvent createEvent(
            @PathParam("calendarId") String calendarId,
            CalendarEvent event);

    @POST
    @Path("/freeBusy")
    FreeBusyResponse queryFreeBusy(FreeBusyRequest request);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
