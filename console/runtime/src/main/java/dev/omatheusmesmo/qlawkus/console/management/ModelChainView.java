package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.model.ModelChainStatus;
import io.quarkus.qute.TemplateData;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** The model chain as the Model card renders it: plain strings and flags, nothing to resolve by reflection. */
@TemplateData
public record ModelChainView(List<TierRow> tiers, List<PermanentRow> permanent, String lastTier, TimeLabel lastAt) {

    @TemplateData
    public record TierRow(String name, boolean enabled, String state, boolean failing, boolean healthy) {
    }

    @TemplateData
    public record PermanentRow(String surface, int status, int consecutive) {
    }

    static ModelChainView of(ModelChainStatus.Snapshot snapshot, ZoneId zone) {
        List<TierRow> tiers = snapshot.tiers().stream()
                .map(tier -> new TierRow(tier.name(), tier.enabled(), tier.state(),
                        "OPEN".equals(tier.state()) || "HALF_OPEN".equals(tier.state()),
                        "CLOSED".equals(tier.state())))
                .toList();
        List<PermanentRow> permanent = snapshot.permanent().stream()
                .map(entry -> new PermanentRow(entry.surface(), entry.status(), entry.consecutive()))
                .toList();
        String tier = snapshot.lastServed().map(ModelChainStatus.Served::tier).orElse(null);
        Instant at = snapshot.lastServed().map(ModelChainStatus.Served::at).orElse(null);
        return new ModelChainView(tiers, permanent, tier, TimeLabel.of(at, zone));
    }
}
