#!/usr/bin/env bash
set -euo pipefail

runner_jar="${1:?Pass the runner JAR from the Quarkus native-sources directory}"
config=$(unzip -p "$runner_jar" META-INF/native-image/reflect-config.json)
payloads=(
    ImmutableGatewayData
    ImmutableReadyData
    ImmutableGuildCreateData
    ImmutableChannelData
    ImmutableMessageData
    ImmutableInteractionData
    gateway.ImmutableReady
    gateway.ImmutableGuildCreate
    gateway.ImmutableMessageCreate
    gateway.ImmutableInteractionCreate
)

missing=0
for payload in "${payloads[@]}"; do
    for suffix in '' '$Json'; do
        type="discord4j.discordjson.json.$payload$suffix"
        if ! jq -e --arg type "$type" \
            'any(.[]; .name == $type and .allDeclaredConstructors and .allDeclaredMethods)' \
            <<< "$config" > /dev/null; then
            echo "Missing native reflection registration for $type" >&2
            missing=1
        fi
    done
done
for cache in SI PW; do
    type="com.github.benmanes.caffeine.cache.$cache"
    if ! jq -e --arg type "$type" 'any(.[]; .name == $type and .allDeclaredConstructors)' \
        <<< "$config" > /dev/null; then
        echo "Missing native reflection registration for $type" >&2
        missing=1
    fi
done
exit "$missing"
