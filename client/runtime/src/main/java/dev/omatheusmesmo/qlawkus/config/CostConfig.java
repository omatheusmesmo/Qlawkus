package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Token prices, used to turn the token counters quarkus-langchain4j already reports into a cost
 * figure on {@code gen_ai.client.estimated_cost}.
 *
 * <p>Prices are per million tokens because that is how every provider publishes them, so a rate can
 * be copied from a pricing page without conversion.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.cost")
public interface CostConfig {

    /**
     * Whether to estimate the cost of model calls. Off by default: an estimate carries the authority
     * of a measurement, and a stale price is worse than no number at all, so it is opt-in once real
     * rates have been entered.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Currency reported alongside the estimate. Only a label - no conversion is applied, so it must
     * match the currency the configured prices are written in.
     */
    @WithDefault("USD")
    String currency();

    /**
     * Price per million input (prompt) tokens, applied to any model without a specific entry in
     * {@link #models()}.
     */
    @WithDefault("0")
    BigDecimal inputPerMillion();

    /**
     * Price per million output (completion) tokens, applied to any model without a specific entry in
     * {@link #models()}.
     */
    @WithDefault("0")
    BigDecimal outputPerMillion();

    /**
     * Per-model prices, overriding the defaults above. The key is matched against the model name the
     * provider reports, so an agent whose primary and fallback are priced differently - or whose
     * chat and embedding models are - can price each one.
     */
    Map<String, ModelPrice> models();

    interface ModelPrice {

        /** Price per million input (prompt) tokens for this model. */
        BigDecimal inputPerMillion();

        /** Price per million output (completion) tokens for this model. */
        BigDecimal outputPerMillion();
    }
}
