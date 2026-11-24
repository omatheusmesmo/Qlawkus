# Web fetch

The optional page-reading extension.

Behavior and configuration are documented for users in `site/content/web-fetch.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

## tools/qlawkus-tools-web-fetch

**`tools/qlawkus-tools-web-fetch`** - Optional page-reading extension (`deployment/` + `runtime/`, capability `web-fetch`, config root `qlawkus.web-fetch.*`, `enabled` default `false`; `app/` turns it on).
`WebFetchTool.fetchUrl(url, format)` is the `@QlawTool`; the pair of web tools is deliberate: `web-search` finds links and never fetches them, this one fetches and never searches.
`PageFetcher` is hand-written on the Vert.x HTTP client for a stated reason: the URL is the model's, so each hop of a redirect chain must pass `FetchPolicy` before a connection opens, which a REST Client interface cannot express.
The client never follows redirects and sends no `Accept-Encoding`, so the hop limit, the byte cap and the absence of decompression bombs are enforced here, and `@Retry` covers one HTTP call (a `TransientHttpException`: 429, 5xx, network failure), never the redirect chain; a timeout is permanent (`FetchFailure`) because retrying a slow page multiplies the wait.
The connection is pinned: `FetchPolicy.resolve` returns the addresses it validated and the request connects to one of them through `RequestOptions.setServer` while the URL's host still supplies `Host`, SNI and the certificate check, so nothing is resolved twice and DNS rebinding is closed (`PinnedConnectionTest`; the JDK client cannot connect to a chosen address and keep the host name for TLS, which is why it is not used).
`FetchPolicy` (`@DefaultBean`, a protected constructor for a test `@Alternative` that reaches a loopback server, as `TestingFetchPolicy` does) refuses non-http(s) schemes, credentials in the URL, `blocked-domains` (suffix match for names, exact for address literals) and any resolved address that is loopback, link-local (cloud metadata included), private, carrier-grade NAT, multicast, unique-local, reserved, or an IPv4 carried in NAT64 or 6to4.
`HtmlText` (jsoup, BOM-managed) renders markdown or plain text, drops scripts, chrome and non-http(s) links; `WebFetchFrame` frames the result as `<web-fetch-data>` untrusted data in the `TelemetryFrame` style, defusing marker lookalikes in the page, the preamble and the source.
Only `text/*`, JSON and XML bodies are read; any other content type is refused unread.
See `site/content/web-fetch.adoc`.
