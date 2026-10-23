# Observability - reference dashboard

`grafana/qlawkus-agent.json` is a Grafana dashboard for the metrics the agent publishes when the
`observability` capability is selected. It ships with the code because a dashboard is part of what
an integrator receives: one that lives only on someone's server is not a deliverable, and it drifts
from the metrics the day a meter is renamed.

It cannot drift silently here. `DashboardMetricsTest` (in `integration-tests/markdown-only`) fails
the build when a panel queries a series the agent does not publish, and when a Qlawkus meter is not
charted anywhere.

## Bundled with the Compose stacks

`./run.sh prod` (and `native`, `local`) already runs it: the `lgtm` service (`docker/lgtm/`) is
`grafana/otel-lgtm` with this file mounted as Grafana's home dashboard, Prometheus scraping the
agent, and traces and logs arriving over OTLP. Open http://localhost:3000 and log in as `admin` with
the `GRAFANA_ADMIN_PASSWORD` from `.env`. Details in `site/content/operations.adoc`, "The bundled
stack".

## Import into your own Grafana

1. Point Prometheus at the agent's scrape endpoint, `/q/metrics` on the management port 9000 (never
   on the public port).
2. In Grafana, **Dashboards > New > Import**, upload `grafana/qlawkus-agent.json`, and pick the
   Prometheus data source when asked.

## What it shows

| Row | Answers |
|-----|---------|
| Turns and the model | How many turns, how long they take, how long each model call takes, tokens spent. |
| Active memory | Whether recall worked: hit ratio, facts injected, their scores, the latency it adds. |
| Tools | Calls and failures per tool, and which tool is slow. |
| Stores | Operations and latency per store and backend (markdown, pgvector, hybrid). |
| Embedding | Facts embedded, and how many were split for being over the chunk budget. |
| Background jobs | Duration and work per job run. |
| Model resilience | Circuit breaker state per surface and workload, and fallbacks to the secondary model. |

The metrics answer "how much" and "how often". For "why was this turn slow", turn tracing on and
open the turn's trace: every tool call is a span under it (see `site/content/operations.adoc`).
