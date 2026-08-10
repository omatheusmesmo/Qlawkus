package dev.omatheusmesmo.qlawkus.model;

import dev.omatheusmesmo.qlawkus.config.CostConfig;
import io.quarkiverse.langchain4j.cost.CostEstimator;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Prices the token counts quarkus-langchain4j already reports, which is what turns on the
 * {@code gen_ai.client.estimated_cost} metric. An agent that runs six cron jobs alone overnight
 * spends without anyone watching, so cost per job and per channel is a number the owner wants before
 * the invoice rather than after.
 *
 * <p>A distribution whose billing is not a flat per-token rate - tiered pricing, a committed-spend
 * contract, a self-hosted model that costs nothing per call - declares its own {@link CostEstimator}
 * at the default priority, and it is consulted first.
 *
 * <p>Reports nothing unless {@code qlawkus.cost.enabled} is set. An estimate is indistinguishable
 * from a measurement once it reaches a dashboard, and a price left at its default would be a
 * confident zero.
 *
 * <p>The priority sits below an unannotated bean (0) so a consuming application's own estimator is
 * consulted first, and above the provider built-ins, which sit at {@link Integer#MIN_VALUE} for that
 * same reason. Ordering is the whole selection mechanism here: {@code CostEstimatorService} injects
 * {@code @All List<CostEstimator>} and takes the first one whose {@code supports} returns true, so
 * {@code @DefaultBean} would be the wrong tool - it suppresses registration rather than ranking it,
 * and other {@code CostEstimator} beans are always present once a provider extension is on the
 * classpath.
 */
@Priority(-100)
@ApplicationScoped
public class TokenPriceCostEstimator implements CostEstimator {

    private static final BigDecimal MILLION = new BigDecimal(1_000_000);

    @Inject
    CostConfig config;

    /**
     * Declines any model this configuration has no price for, rather than claiming every call and
     * answering zero. The provider extensions ship estimators with real published rates for their own
     * models; a catch-all that always matched would shadow those and silently replace a correct
     * figure with an unconfigured one.
     */
    @Override
    public boolean supports(SupportsContext context) {
        return config.enabled() && hasPriceFor(context.model());
    }

    private boolean hasPriceFor(String model) {
        CostConfig.ModelPrice price = config.models().get(model);
        if (price != null) {
            return true;
        }
        return config.inputPerMillion().signum() != 0 || config.outputPerMillion().signum() != 0;
    }

    @Override
    public CostResult estimate(CostContext context) {
        BigDecimal input = cost(context.inputTokens(), inputPerMillion(context.model()));
        BigDecimal output = cost(context.outputTokens(), outputPerMillion(context.model()));
        return new CostResult(input, output, config.currency());
    }

    /**
     * Token counts are optional on the response: a provider that does not report usage leaves them
     * null, and a call priced at zero is a truer statement than one priced on a guess.
     */
    private static BigDecimal cost(Integer tokens, BigDecimal perMillion) {
        if (tokens == null || tokens <= 0 || perMillion.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return perMillion.multiply(new BigDecimal(tokens)).divide(MILLION, MathContext.DECIMAL64);
    }

    private BigDecimal inputPerMillion(String model) {
        CostConfig.ModelPrice price = config.models().get(model);
        return price == null ? config.inputPerMillion() : price.inputPerMillion();
    }

    private BigDecimal outputPerMillion(String model) {
        CostConfig.ModelPrice price = config.models().get(model);
        return price == null ? config.outputPerMillion() : price.outputPerMillion();
    }
}
