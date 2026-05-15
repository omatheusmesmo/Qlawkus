package dev.omatheusmesmo.qlawkus.messaging.format;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;

public interface MessageFormatter {

    MessagingFormat format();

    String format(String text);
}
