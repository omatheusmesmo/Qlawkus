package dev.omatheusmesmo.qlawkus.task;

import java.time.Duration;

/** Delivery of a bounded, preformatted notice to a previously validated owner destination. */
public interface OwnerNoticeDelivery {

    boolean isAvailable(String provider);

    void send(TaskDestination destination, String notice, Duration remaining);
}
