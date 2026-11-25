#!/usr/bin/env bash
# Fails when the docs drift from the product: every console page in the sidebar needs a section in
# console.adoc and a screenshot in both themes, every diagram needs <title> and <desc>, every
# image an page references must exist, and every xref target must be a page that exists.
# Usage: scripts/check-docs-content.sh   (from anywhere; no build, no network)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
content="$root/site/content"
nav="$root/console/runtime/src/main/resources/templates/console/base.html"
status=0

fail() { echo "FAIL: $*" >&2; status=1; }

pages=$(grep -o 'href="/console[^"]*"' "$nav" | sed 's/href="//; s/"$//' | grep -v '\.css$' | sort -u)
[ -n "$pages" ] || fail "no console pages found in $nav"

for href in $pages; do
  slug="${href#/console}"; slug="${slug#/}"; slug="${slug:-overview}"
  grep -q "\`$href\`" "$content/console.adoc" || fail "console.adoc has no section naming \`$href\`"
  for theme in light dark; do
    [ -s "$root/site/public/images/console/$slug-$theme.png" ] || fail "missing screenshot images/console/$slug-$theme.png"
  done
done

for svg in composition-flow memory-layers model-chain redeploy-sequence shutdown-sequence; do
  f="$root/site/public/images/diagrams/$svg.svg"
  [ -s "$f" ] || { fail "missing diagram $f"; continue; }
  grep -q '<title' "$f" || fail "$svg.svg has no <title>"
  grep -q '<desc' "$f" || fail "$svg.svg has no <desc>"
  grep -q 'prefers-color-scheme: dark' "$f" || fail "$svg.svg has no dark palette"
done

while IFS= read -r ref; do
  [ -s "$root/site/public/images/$ref" ] || fail "image reference does not exist: $ref"
  [ -s "$root/docs/modules/ROOT/images/$ref" ] || fail "image missing from the Antora mirror: $ref"
done < <(grep -rhoE '(image::?|src="|srcset=")\{(image-root|html-image-root)\}[^["]+' "$content"/*.adoc | sed -E 's/^(image::?|src="|srcset=")\{(image-root|html-image-root)\}//' | sort -u)

if grep -rnE '(image::?|src="|srcset=")\.\./images/' "$content"/*.adoc; then
  fail "an image path is hard-coded to ../images/; use {image-root} or {html-image-root} so the Roq site and Antora both resolve it"
fi

while IFS= read -r ref; do
  page="${ref%%#*}"
  [ -e "$content/$page" ] || fail "xref to a page that does not exist: $ref"
done < <(grep -rhoE 'xref:[^[]+' "$content"/*.adoc | sed 's/^xref://' | sort -u)

for guide in quickstart; do
  grep -q "^== .*[Ff]irst agent in " "$content/$guide.adoc" || fail "$guide.adoc has no 'first agent' guide"
done
grep -q "^== Connect a channel" "$content/messaging.adoc" || fail "messaging.adoc has no 'Connect a channel' guide"
grep -q "^== Add a capability and rebuild" "$content/composition.adoc" || fail "composition.adoc has no 'Add a capability and rebuild' guide"
grep -q "^== Back up and restore state" "$content/storage.adoc" || fail "storage.adoc has no 'Back up and restore state' guide"
grep -q "^=== Read an agent turn in Grafana" "$content/operations.adoc" || fail "operations.adoc has no 'Read an agent turn in Grafana' guide"

[ "$status" -eq 0 ] && echo "docs content check passed"
exit "$status"
