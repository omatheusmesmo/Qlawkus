package dev.omatheusmesmo.qlawkus.messaging.format;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class FormatterRegistry {

    @Inject
    Instance<MessageFormatter> formatters;

    private final Map<MessagingFormat, MessageFormatter> registry = new EnumMap<>(MessagingFormat.class);

    @PostConstruct
    void init() {
        for (MessageFormatter formatter : formatters) {
            registry.put(formatter.format(), formatter);
        }
    }

    public MessageFormatter forFormat(MessagingFormat format) {
        return registry.getOrDefault(format, PLAIN_TEXT_FALLBACK);
    }

    private static final MessageFormatter PLAIN_TEXT_FALLBACK = new MessageFormatter() {
        @Override
        public MessagingFormat format() { return MessagingFormat.PLAIN_TEXT; }

        @Override
        public String format(String text) { return text == null ? "" : text; }
    };
}
