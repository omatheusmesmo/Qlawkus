package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.omatheusmesmo.qlawkus.config.CostConfig;
import io.quarkiverse.langchain4j.cost.CostEstimator;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the arithmetic behind {@code gen_ai.client.estimated_cost}. A cost series is read as fact once
 * it reaches a dashboard, so the cases that matter here are the ones that would publish a confident
 * wrong number: prices left at their default, a provider that reports no token counts, and a model
 * whose rate differs from the fallback's.
 */
class TokenPriceCostEstimatorTest {

    @Test
    void estimatingIsOffUntilPricesAreEntered() {
        assertFalse(estimator(config(false, "0.5", "1.5", Map.of())).supports(context("any", 1, 1)),
                "a default-priced zero would look like a measured zero");
    }

    @Test
    void anUnpricedModelIsLeftToTheProviderBuiltIns() {
        assertFalse(estimator(config(true, "0", "0", Map.of())).supports(context("gpt-4o", 1, 1)),
                "claiming every call and answering zero would shadow the provider estimator that knows the real rate");
    }

    @Test
    void pricesInputAndOutputSeparately() {
        CostEstimator.CostResult result = estimator(config(true, "2", "10", Map.of()))
                .estimate(context("primary", 1_000_000, 500_000));

        assertEquals(0, new BigDecimal("2").compareTo(result.inputTokensCost()));
        assertEquals(0, new BigDecimal("5").compareTo(result.outputTokensCost()),
                "output is priced on its own rate, which is where the bill usually grows");
        assertEquals("USD", result.currency());
    }

    @Test
    void aPerModelRateOverridesTheDefault() {
        CostConfig config = config(true, "2", "10",
                Map.of("ollama-local", price("0", "0")));

        CostEstimator.CostResult result = estimator(config)
                .estimate(context("ollama-local", 1_000_000, 1_000_000));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.inputTokensCost()),
                "a self-hosted fallback must not be billed at the primary's rate");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.outputTokensCost()));
    }

    @Test
    void anUnpricedModelFallsBackToTheDefaultRate() {
        CostEstimator.CostResult result = estimator(config(true, "2", "10", Map.of("other", price("99", "99"))))
                .estimate(context("primary", 1_000_000, 0));

        assertEquals(0, new BigDecimal("2").compareTo(result.inputTokensCost()));
    }

    /**
     * Not every provider reports usage. Charging nothing states what is known; inferring a count
     * would invent the number the metric exists to report.
     */
    @Test
    void missingTokenCountsCostNothing() {
        CostEstimator.CostResult result = estimator(config(true, "2", "10", Map.of()))
                .estimate(context("primary", null, null));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.inputTokensCost()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.outputTokensCost()));
    }

    @Test
    void aRateOfZeroCostsNothingEvenWithTokens() {
        CostEstimator.CostResult result = estimator(config(true, "0", "0", Map.of("free", price("0", "0"))))
                .estimate(context("free", 5_000, 5_000));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.inputTokensCost()));
    }

    @Test
    void supportsFollowsTheEnabledFlag() {
        assertTrue(estimator(config(true, "1", "1", Map.of())).supports(context("primary", 1, 1)));
    }

    // ------------------------------------------------------------------ setup

    private static TokenPriceCostEstimator estimator(CostConfig config) {
        TokenPriceCostEstimator estimator = new TokenPriceCostEstimator();
        estimator.config = config;
        return estimator;
    }

    private static CostConfig.ModelPrice price(String input, String output) {
        return new CostConfig.ModelPrice() {
            @Override
            public BigDecimal inputPerMillion() {
                return new BigDecimal(input);
            }

            @Override
            public BigDecimal outputPerMillion() {
                return new BigDecimal(output);
            }
        };
    }

    private static CostConfig config(boolean enabled, String input, String output,
            Map<String, CostConfig.ModelPrice> models) {
        return new CostConfig() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String currency() {
                return "USD";
            }

            @Override
            public BigDecimal inputPerMillion() {
                return new BigDecimal(input);
            }

            @Override
            public BigDecimal outputPerMillion() {
                return new BigDecimal(output);
            }

            @Override
            public Map<String, ModelPrice> models() {
                return models;
            }
        };
    }

    private static CostEstimator.CostContext context(String model, Integer input, Integer output) {
        return new CostEstimator.CostContext() {
            @Override
            public Integer inputTokens() {
                return input;
            }

            @Override
            public Integer outputTokens() {
                return output;
            }

            @Override
            public String model() {
                return model;
            }

            /**
             * Unused by this estimator, which prices from token counts alone. It exists on the SPI so
             * an estimator can reach the raw response - a provider reporting prompt cache tokens, for
             * instance - and the tests stay honest about not needing it.
             */
            @Override
            public ChatModelResponseContext responseContext() {
                return null;
            }
        };
    }
}
