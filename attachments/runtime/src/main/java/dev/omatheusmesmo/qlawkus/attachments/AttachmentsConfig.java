package dev.omatheusmesmo.qlawkus.attachments;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import java.util.Optional;

/** Model routing for owner-supplied attachments. */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.attachments")
public interface AttachmentsConfig {
    enum ModelTier { PRIMARY, SECONDARY, FALLBACK }

    /**
     * Existing model tier that accepts image input. Configure this only after verifying that the
     * selected model supports images. With no selection images are refused.
     */
    Optional<ModelTier> imageModel();

    /**
     * Existing model tier that accepts the uploaded PDF as a file. Configure this only after
     * verifying PDF support on the selected endpoint. With no selection, Qlawkus extracts bounded
     * text locally before sending it to a model.
     */
    Optional<ModelTier> pdfModel();
}
