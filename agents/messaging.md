# Messaging and attachments

The messaging adapters and chat attachments.

Behavior and configuration are documented for users in `site/content/messaging.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

## messaging/

**`messaging/`** - Messaging extension (core + Discord/Telegram/Slack/WhatsApp adapters).
Same `deployment/` + `runtime/` pattern.
The voice clients are REST Client interfaces too: `transcription.WhisperApiClient` (multipart `ClientMultipartForm`, `verbose_json` plus the ISO `language` field only on the voice-session path), `tts.OpenAiSpeechApi` and `tts.ElevenLabsSpeechApi`, configured under `quarkus.rest-client.{whisper-api,tts-openai-api,tts-elevenlabs-api}.*` (15 s connect, 120 s read) with the base URL passed per call from `qlawkus.messaging.transcription.base-url` / `qlawkus.messaging.tts.providers.<lang>.base-url`.
They retry transient failures like every other client; a TTS provider's `fallback` is therefore tried only after those retries.
Turns run through the blocking agent call (`AgentRunner`, falling back to `AgentService.chatSync`), never through `AgentStream`: tools on the streaming path run on another thread without the caller's request and delivery context (#398).
Tool progress is fed by the AI service's CDI events: `messaging/runtime`'s `progress.ToolProgressRouter` observes `AiServiceResponseReceivedEvent` (a tool request is a start) and `ToolExecutedEvent` (a tool end) for the `AgentService` and `QlawkusAgent` selectors, routes them by memory id to the turn registered for that id, and ignores everything else.
The `progress` package drives one throttled status message per turn for the adapters that expose `MessagingProvider.progressSink()` (Discord, Telegram); `/verbose` cycles a chat's mode in memory.
Discord uses Discord4J with the matched `discord-json` and `discord-json-api` versions managed in the root POM.
`DiscordProcessor` registers generated Jackson payloads for native reflection.
The PR build checks the generated metadata in the messaging integration app before native compilation.
`DiscordProviderAdapter` retries failed Gateway logins with capped backoff, and the optional `discord-gateway` readiness check reports a disconnected bot as degraded while staying UP.
Discord's edge resets idle connections, so the adapter hands Discord4J a `ReactorResources` whose pool evicts idle connections in the background (`HTTP_MAX_IDLE_TIME`, `HTTP_EVICT_INTERVAL`); the stock client keeps them forever and a once-a-day task found only dead ones.
`SEND_RETRY` (3 retries, backoff, `IOException` only) covers `send`, `sendApprovalPrompt`, `sendFile` and `sendVoice`; each send builds one enforced nonce and the file spec is rebuilt per attempt because its body stream is consumed by a failed one. Telegram already retries through `@Retry(maxRetries = 3)` on `TelegramBotClient`.
The messaging allowlist (`qlawkus.messaging.auth.allowed-users.<provider>`) is fail-closed like the admin auth: a missing or empty list rejects every sender, and `*` is the explicit allow-all; `MessagingAllowlistStartupWarnings` logs a WARN per active channel that has no list or accepts `*`.

## attachments/

**`attachments/`** - Optional Quarkus extension (`deployment/` + `runtime/`, capability `attachments`).
The core `AttachmentChat` SPI accepts request-scoped `ChatAttachment` handles.
The console uploads through multipart `/api/chat` and consumes the existing SSE format.
Discord and Telegram resolve their own provider media only after the messaging allowlist accepts the turn.
Telegram document and photo webhook updates also require its Bot API shared-secret header before dispatch.
The attachment extension bounds file size, PDF extraction and image type checks before sending labelled text and LangChain4j `ImageContent` to the model.
Attachment analysis calls the model outside the persistent conversation and tool loop.
Operators select a tier for LangChain4j `ImageContent` via `qlawkus.attachments.image-model` and a tier for `PdfFileContent` via `qlawkus.attachments.pdf-model`.
The PDF tier defaults to local text extraction.
The local parser uses the Quarkiverse PDFBox extension for resources and native AWT support, with per-file limits on the extraction itself.
See `site/content/messaging.adoc`.
