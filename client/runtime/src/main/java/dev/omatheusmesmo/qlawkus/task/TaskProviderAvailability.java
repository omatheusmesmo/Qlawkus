package dev.omatheusmesmo.qlawkus.task;

/** Optional view of the delivery providers enabled in the running distribution. */
public interface TaskProviderAvailability {

    boolean isAvailable(String providerId);
}
