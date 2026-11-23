package dev.omatheusmesmo.qlawkus.task;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

@ApplicationScoped
class TaskDestinationResolver {

    private final OwnerChatDirectory ownerChats;
    private final DynamicTaskScheduler scheduler;
    private final Instance<TaskProviderAvailability> availability;

    TaskDestinationResolver(OwnerChatDirectory ownerChats, DynamicTaskScheduler scheduler,
            Instance<TaskProviderAvailability> availability) {
        this.ownerChats = ownerChats;
        this.scheduler = scheduler;
        this.availability = availability;
    }

    TaskDestination resolve(AgentDeliveryContext.Snapshot origin, String provider) {
        if ("rest".equals(provider)) {
            throw new Problem("INVALID_DESTINATION", "scheduled replies need a messaging channel");
        }
        if (provider != null) {
            requireAvailable(provider);
        }
        TaskDestination resolved = choose(origin, provider);
        if (provider == null) {
            requireAvailable(resolved.providerId());
        }
        return resolved;
    }

    private TaskDestination choose(AgentDeliveryContext.Snapshot origin, String provider) {
        if (origin.chatId() != null && origin.providerId() != null
                && (provider == null || provider.equalsIgnoreCase(origin.providerId()))) {
            return new TaskDestination(origin.providerId(), origin.chatId());
        }
        Optional<OwnerChat> known = provider == null ? ownerChats.mostRecent() : ownerChats.lastChat(provider);
        if (known.isPresent()) {
            OwnerChat chat = known.get();
            return new TaskDestination(chat.providerId(), chat.chatId());
        }
        List<ScheduledTask> older = savedTasks(provider);
        List<TaskDestination> verified = older.stream().filter(TaskDestinationResolver::matchesOrigin)
                .map(ScheduledTask::destination).distinct().toList();
        if (verified.size() == 1) {
            return verified.getFirst();
        }
        if (verified.size() > 1) {
            if (provider == null && verified.stream().map(TaskDestination::providerId).distinct().count() > 1) {
                throw new Problem("AMBIGUOUS_DESTINATION", "more than one messaging provider is available; ask which provider to use");
            }
            throw new Problem("AMBIGUOUS_DESTINATION", "more than one chat is known; ask the owner to write from the chat that should receive the reminder");
        }
        if (!older.isEmpty()) {
            throw new Problem("UNCONFIRMED_DESTINATION", "an older schedule has no verified chat of origin; "
                    + "ask the owner to send one message from the messaging chat that should receive the reminder");
        }
        if (provider != null) {
            throw new Problem("NO_DESTINATION", "no chat to deliver to on " + displayName(provider)
                    + " yet: write to me there first");
        }
        throw new Problem("NO_DESTINATION", "no chat to deliver to yet: write to me on Telegram or Discord first");
    }

    private void requireAvailable(String provider) {
        if (availability.isResolvable() && !availability.get().isAvailable(provider)) {
            throw new Problem("PROVIDER_UNAVAILABLE", displayName(provider)
                    + " is unavailable; ask the owner whether to enable it or name a different provider");
        }
    }

    String describe(AgentDeliveryContext.Snapshot origin) {
        var providers = new TreeSet<String>();
        if (origin.providerId() != null && origin.chatId() != null) {
            providers.add(origin.providerId());
        }
        ownerChats.knownChats().forEach(chat -> providers.add(chat.providerId()));
        savedTasks(null).forEach(task -> providers.add(task.destination().providerId()));
        if (providers.isEmpty()) {
            return "No messaging destinations are known yet. Ask the owner to send a message from the intended chat.";
        }
        var report = new StringBuilder("Scheduling destinations (provider names only):");
        for (String provider : providers) {
            report.append("\n- ").append(provider).append(": ");
            try {
                resolve(origin, provider);
                report.append("available");
            } catch (Problem problem) {
                report.append(problem.code()).append(" — ").append(problem.getMessage());
            }
        }
        return report.toString();
    }

    private List<ScheduledTask> savedTasks(String provider) {
        return scheduler.list().stream().map(TaskView::task)
                .filter(task -> task.status() == TaskStatus.ACTIVE)
                .filter(task -> !"rest".equalsIgnoreCase(task.destination().providerId()))
                .filter(task -> provider == null || provider.equalsIgnoreCase(task.destination().providerId()))
                .toList();
    }

    /*
     * The old tool accepted an explicit chat id. Only a record whose destination matches its
     * provider-qualified conversation proves it came from that chat. Shared memory ids cannot do that.
     */
    private static boolean matchesOrigin(ScheduledTask task) {
        TaskDestination destination = task.destination();
        return task.memoryId().equals(destination.providerId() + ":" + destination.chatId());
    }

    static String providerName(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String displayName(String provider) {
        return Character.toUpperCase(provider.charAt(0)) + provider.substring(1);
    }

    static final class Problem extends IllegalArgumentException {
        private final String code;

        Problem(String code, String message) {
            super(message);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
