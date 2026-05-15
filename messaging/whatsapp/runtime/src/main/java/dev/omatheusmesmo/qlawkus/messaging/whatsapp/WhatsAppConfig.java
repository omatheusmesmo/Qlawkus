package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging.whatsapp")
public interface WhatsAppConfig {

    /**
     * WhatsApp Business API access token from Meta Business Suite.
     */
    String accessToken();

    /**
     * WhatsApp Business phone number ID.
     */
    String phoneNumberId();

    /**
     * Webhook verification token configured in Meta Developer Console.
     */
    String verifyToken();
}
