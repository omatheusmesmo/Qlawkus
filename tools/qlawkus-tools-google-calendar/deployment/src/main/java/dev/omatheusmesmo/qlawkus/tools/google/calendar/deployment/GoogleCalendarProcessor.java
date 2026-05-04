package dev.omatheusmesmo.qlawkus.tools.google.calendar.deployment;

import dev.omatheusmesmo.qlawkus.tools.google.calendar.CalendarTool;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.GoogleCalendarConfig;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.GoogleCalendarRestClient;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEvent;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventAttendee;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventList;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.EventDateTime;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyRequest;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyResponse;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class GoogleCalendarProcessor {

    private static final String FEATURE = "google-calendar";
    private static final String REST_CLIENT_CAPABILITY = "io.quarkus.rest-client.jackson";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsCalendarEnabled.class)
    AdditionalBeanBuildItem registerCalendarBeans(Capabilities capabilities) {
        if (capabilities.isMissing(REST_CLIENT_CAPABILITY)) {
            throw new ConfigurationException(
                    "Google Calendar tool requires quarkus-rest-client-jackson. "
                    + "Add the extension or disable the calendar with qlawkus.google.calendar.enabled=false");
        }
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(CalendarTool.class)
                .addBeanClass(GoogleCalendarConfig.class)
                .addBeanClass(GoogleCalendarRestClient.class)
                .setRemovable()
                .build();
    }

    @BuildStep(onlyIf = IsCalendarEnabled.class)
    ReflectiveClassBuildItem registerCalendarReflection() {
        return ReflectiveClassBuildItem.builder(
                CalendarTool.class.getName(),
                GoogleCalendarConfig.class.getName(),
                CalendarEventList.class.getName(),
                CalendarEvent.class.getName(),
                EventDateTime.class.getName(),
                CalendarEventAttendee.class.getName(),
                FreeBusyRequest.class.getName(),
                FreeBusyRequest.FreeBusyItem.class.getName(),
                FreeBusyResponse.class.getName(),
                FreeBusyResponse.FreeBusyCalendar.class.getName(),
                FreeBusyResponse.TimeRange.class.getName()
        ).methods().fields().build();
    }
}
