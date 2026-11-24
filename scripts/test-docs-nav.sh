#!/usr/bin/env bash
# Logic test for docs/nav/DocsNav.java and scripts/check-docs-drift.sh against throwaway fixtures:
# the navigation is generated from the :page-nav-*: attributes, a page without them fails the
# build, and a hand edit of a generated file is reported as drift. Usage: scripts/test-docs-nav.sh
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

fail() { echo "FAIL: $1"; failures=$((failures + 1)); }
pass() { echo "ok:   $1"; }

fixture() {
  local dir="$1"
  rm -rf "$dir"
  mkdir -p "$dir/site/content/includes" "$dir/docs/modules/ROOT"
  printf '= Alpha\n:page-title: Alpha\n:page-nav-group: First\n:page-nav-order: 10\n\nbody\n' > "$dir/site/content/alpha.adoc"
  printf '= Gamma Long Heading\n:page-title: Gamma\n:page-nav-group: Second\n:page-nav-order: 30\n:page-nav-title: Short\n\nbody\n' > "$dir/site/content/gamma.adoc"
  printf '= Beta\n:page-nav-group: First\n:page-nav-order: 20\n\nbody\n' > "$dir/site/content/beta.adoc"
  printf 'generated\n' > "$dir/site/content/includes/ignored.adoc"
}

nav="$root/docs/nav/DocsNav.java"
f="$work/ok"
fixture "$f"

if java "$nav" "$f" > "$work/out" 2>&1; then pass "generate succeeds on a complete fixture"; else fail "generate failed: $(cat "$work/out")"; fi

data="$f/site/data/nav.yml"
antora="$f/docs/modules/ROOT/nav.adoc"

[ -f "$data" ] && [ -f "$antora" ] && pass "both navigation files are written" || fail "navigation files missing"

order="$(grep -o 'path: [a-z]*' "$data" 2>/dev/null | tr '\n' ' ')"
[ "$order" = "path: alpha path: beta path: gamma " ] && pass "pages follow nav-order" || fail "order was: $order"

grep -q 'file: alpha.adoc' "$data" && pass "each entry carries its source file" || fail "source file missing"
grep -q 'title: Short' "$data" && pass "nav-title overrides the title" || fail "nav-title ignored"
grep -q 'title: Beta' "$data" && pass "title falls back to the heading" || fail "heading fallback missing"
grep -q 'title: Gamma Long' "$data" && fail "heading used although page-title exists" || pass "page-title wins over the heading"
grep -q 'ignored' "$data" "$antora" && fail "includes are indexed" || pass "includes are not pages"

first="$(grep -n 'title: First' "$data" | cut -d: -f1)"
second="$(grep -n 'title: Second' "$data" | cut -d: -f1)"
[ -n "$first" ] && [ -n "$second" ] && [ "$first" -lt "$second" ] && pass "groups follow their lowest order" || fail "group order wrong"

grep -q 'xref:alpha.adoc\[Alpha\]' "$antora" && grep -q 'xref:gamma.adoc\[Short\]' "$antora" \
  && pass "Antora nav lists every page with its nav title" || fail "Antora nav incomplete"

cp "$data" "$work/data.1"
cp "$antora" "$work/antora.1"
java "$nav" "$f" > /dev/null 2>&1
cmp -s "$data" "$work/data.1" && cmp -s "$antora" "$work/antora.1" && pass "generation is idempotent" || fail "second run changed output"

printf '= Delta\n:page-nav-group: First\n:page-nav-order: 15\n\nbody\n' > "$f/site/content/delta.adoc"
java "$nav" "$f" > /dev/null 2>&1
grep -q 'path: delta' "$data" && grep -q 'xref:delta.adoc\[Delta\]' "$antora" \
  && pass "a new page reaches both navs with no other edit" || fail "new page not picked up"

mv "$f/site/content/alpha.adoc" "$f/site/content/alpha.adoc.bak"
printf '= Alpha Renamed\n:page-title: Alpha Renamed\n:page-nav-group: First\n:page-nav-order: 10\n:page-nav-title: Alpha\n\nbody\n' > "$f/site/content/alpha.adoc"
java "$nav" "$f" > /dev/null 2>&1
grep -q 'path: alpha' "$data" && pass "a renamed title keeps the page path" || fail "path changed with the title"

b="$work/missing"
fixture "$b"
printf '= Orphan\n:page-title: Orphan\n\nbody\n' > "$b/site/content/orphan.adoc"
if java "$nav" "$b" > "$work/out" 2>&1; then
  fail "a page without nav attributes did not fail the build"
else
  grep -q 'orphan.adoc' "$work/out" && grep -q 'page-nav-group' "$work/out" \
    && pass "a page without nav attributes fails and is named" || fail "error does not name the page: $(cat "$work/out")"
fi

c="$work/badorder"
fixture "$c"
printf '= Bad\n:page-nav-group: First\n:page-nav-order: soon\n\nbody\n' > "$c/site/content/bad.adoc"
if java "$nav" "$c" > "$work/out" 2>&1; then fail "a non-numeric order was accepted"; else pass "a non-numeric order fails"; fi

drift="$root/scripts/check-docs-drift.sh"
repo="$work/repo"
fixture "$repo"
java "$nav" "$repo" > /dev/null 2>&1
mkdir -p "$repo/docs/modules/ROOT/pages"
cp "$repo/site/content/alpha.adoc" "$repo/docs/modules/ROOT/pages/alpha.adoc"
(cd "$repo" && git init -q && git add -A && git -c user.name=t -c user.email=t@t commit -qm base)

if (cd "$repo" && "$drift" > "$work/out" 2>&1); then pass "drift check passes on a clean tree"; else fail "clean tree reported as drift: $(cat "$work/out")"; fi

echo "edited by hand" >> "$repo/docs/modules/ROOT/pages/alpha.adoc"
if (cd "$repo" && "$drift" > "$work/out" 2>&1); then
  fail "a hand edit of the mirror passed the drift check"
else
  grep -q 'docs/modules/ROOT/pages/alpha.adoc' "$work/out" && pass "a hand edit of the mirror fails and is named" || fail "drift output does not name the file"
fi

(cd "$repo" && git checkout -q -- . && echo "# x" >> site/data/nav.yml)
if (cd "$repo" && "$drift" > "$work/out" 2>&1); then fail "a hand edit of nav.yml passed"; else pass "a hand edit of the generated nav fails"; fi

(cd "$repo" && git checkout -q -- . && echo "x" > site/content/includes/new.adoc)
if (cd "$repo" && "$drift" > "$work/out" 2>&1); then fail "an untracked include passed"; else pass "an uncommitted generated include fails"; fi

if [ "$failures" -gt 0 ]; then echo "$failures check(s) failed"; exit 1; fi
echo "all checks passed"
