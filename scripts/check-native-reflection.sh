#!/usr/bin/env bash
# Fails when a type the native image reaches reflectively is missing from the generated reachability
# metadata. Each entry below was a runtime failure of a published native image that the JVM image did not
# show: the gateway login (Caffeine reads its generated classes' static fields), the gateway's first frame
# (Jackson builds the payload deserializers) and every turn through the declarative agent (langchain4j-agentic
# reflects on its interface). Add a line here for the next one, with the failure it came from.
set -euo pipefail

runner_jar="${1:?Pass the runner JAR from the Quarkus native-sources directory}"
config=$(unzip -p "$runner_jar" META-INF/native-image/reflect/reachability-metadata.json)

missing=0
require() {
    local type="$1"
    shift
    local flags=""
    for flag in "$@"; do
        flags="$flags and .$flag"
    done
    if ! jq -e --arg type "$type" "any(.reflection[]; .type == \$type$flags)" <<< "$config" > /dev/null; then
        echo "Missing native reflection registration for $type ($*)" >&2
        missing=1
    fi
}

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
for payload in "${payloads[@]}"; do
    for suffix in '' '$Json'; do
        require "discord4j.discordjson.json.$payload$suffix" allDeclaredConstructors allDeclaredMethods
    done
done

for cache in SI PW; do
    require "com.github.benmanes.caffeine.cache.$cache" allDeclaredConstructors allDeclaredFields
done

for frame in discord4j.gateway.json.GatewayPayload discord4j.voice.json.VoiceGatewayPayload; do
    require "$frame" allDeclaredConstructors allDeclaredMethods allDeclaredFields
done
for deserializer in discord4j.gateway.json.jackson.PayloadDeserializer discord4j.voice.json.VoiceGatewayPayloadDeserializer; do
    require "$deserializer" allDeclaredConstructors
done

require dev.omatheusmesmo.qlawkus.agent.QlawkusAgent allDeclaredMethods

exit "$missing"
