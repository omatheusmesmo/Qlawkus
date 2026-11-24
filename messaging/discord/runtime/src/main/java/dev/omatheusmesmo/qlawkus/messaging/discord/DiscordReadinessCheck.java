package dev.omatheusmesmo.qlawkus.messaging.discord;

import java.time.Duration;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import discord4j.core.GatewayDiscordClient;
import discord4j.gateway.GatewayClient;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Readiness
public class DiscordReadinessCheck implements AsyncHealthCheck {

    private final DiscordConfig config;
    private final DiscordProviderAdapter adapter;

    DiscordReadinessCheck(DiscordConfig config, DiscordProviderAdapter adapter) {
        this.config = config;
        this.adapter = adapter;
    }

    @Override
    public Uni<HealthCheckResponse> call() {
        if (config.botToken().isEmpty()) {
            return Uni.createFrom().item(response("disabled"));
        }
        GatewayDiscordClient gateway = adapter.gateway();
        int shards = gateway == null ? 0 : gateway.getGatewayClientGroup().getShardCount();
        if (shards == 0) {
            return Uni.createFrom().item(response("degraded"));
        }
        return Uni.createFrom().completionStage(() -> Flux.range(0, shards)
                .flatMap(id -> gateway.getGatewayClient(id).map(GatewayClient::isConnected).orElse(Mono.just(false)))
                .all(Boolean::booleanValue)
                .timeout(Duration.ofSeconds(1))
                .onErrorReturn(false)
                .toFuture())
                .map(connected -> response(connected ? "connected" : "degraded"));
    }

    private HealthCheckResponse response(String status) {
        return HealthCheckResponse.named("discord-gateway").up().withData("status", status).build();
    }
}
