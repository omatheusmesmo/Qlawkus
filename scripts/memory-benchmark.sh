#!/usr/bin/env bash
#
# Personal-agent memory benchmark (adapted from OpenClaw's personal-agent-benchmark-pack).
# Exercises the live agent end-to-end against a real LLM and reports PASS/FAIL per check.
#
# Every recall wipes the working-memory window first, so a correct answer can only come from
# long-term memory (owner profile injection or active-memory retrieval), never the chat window.
#
# Usage:
#   BASE=http://localhost:8742 AUTH=qlawkus:<admin-password> SLEEP=25 ./scripts/memory-benchmark.sh
#
# Requires: curl, and `docker compose exec postgres` for window wipes (override WIPE_CMD otherwise).
set -u

BASE="${BASE:-http://localhost:8742}"
AUTH="${AUTH:-qlawkus:dev}"              # dev-mode credential; for a containerized instance set your configured admin password
SLEEP="${SLEEP:-25}"                      # throttle between LLM turns (rate-limit friendly)
PSQL="${PSQL:-docker compose exec -T postgres psql -U qlawkus -d qlawkus}"

PASS=0; FAIL=0; declare -a FAILED

say()  { curl -s -u "$AUTH" -H 'Content-Type: application/json' -X POST "$BASE/api/chat/sync" -d "{\"message\":\"$1\"}" --max-time 150; }
mem()  { curl -s -u "$AUTH" "$BASE/api/admin/memory"; }
purge(){ curl -s -o /dev/null -X DELETE "$BASE/api/admin/memory?all=true" -u "$AUTH"; }
review(){ curl -s -u "$AUTH" -X POST "$BASE/api/admin/memory/review"; }
wipe() { $PSQL -c "TRUNCATE chat_message_entity;" >/dev/null 2>&1; }
embn() { $PSQL -t -c "SELECT count(*) FROM embeddings;" 2>/dev/null | tr -d ' \n'; }

ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); FAILED+=("$1"); }

recall_has(){ # <label> <query> <expected-substring>
  wipe; local r; r="$(say "$2")"
  if echo "$r" | grep -qi "$3"; then ok "$1  ->  $r"; else bad "$1  (wanted ~$3)  ->  $r"; fi
  sleep "$SLEEP"
}
must_be_unknown(){ # <label> <query>
  wipe; local r; r="$(say "$2")"
  if echo "$r" | grep -qiE "don'?t|not sure|no (info|record|idea)|n[ãa]o (sei|tenho)|haven'?t|do not have"; then
    ok "$1  (correctly uncertain)  ->  $r"; else bad "$1  (possible hallucination)  ->  $r"; fi
  sleep "$SLEEP"
}

echo "############ 0. clean slate ############"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/admin/memory?all=true" -u "$AUTH")" = "204" ] && ok "purge-all -> 204" || bad "purge-all"
echo "  memory: $(mem)"; sleep "$SLEEP"

echo "############ 1. owner-profile capture ############"
say "Hi! I'm Matheus. I work mainly with Java and Quarkus and prefer constructor injection. Please remember who I am." >/dev/null
sleep "$SLEEP"
prof="$($PSQL -t -c "SELECT coalesce(name,'') || '|' || coalesce(profile,'') FROM user_profile WHERE id=1;" 2>/dev/null)"
echo "$prof" | grep -qi "matheus" && ok "owner name captured" || bad "owner name not captured ($prof)"

echo "############ 2. owner recall with ALL memory purged (static profile injection) ############"
purge
echo "  memory now: $(mem)"
recall_has "owner-identity" "What is my name and what do I work with?" "matheus"

echo "############ 3. long-term fact store + recall (paraphrased) ############"
say "Remember these about me: my GitHub handle is omatheusmesmo; I live in Brazil; I dislike the var keyword in Java." >/dev/null
sleep "$SLEEP"
recall_has "github-handle" "Remind me of my GitHub username." "omatheusmesmo"
recall_has "country"       "Which country am I based in?"      "brazil"
recall_has "java-pref"     "How do I feel about the var keyword in Java?" "dislike\|don'?t\|avoid\|not"

echo "############ 4. no hallucination on unknown facts ############"
must_be_unknown "no-sport"    "What is my favorite football team? Say if you don't know."
must_be_unknown "no-birthday" "What is my date of birth? Say if you don't know."

echo "############ 5. semantic dedup (background review) ############"
before="$(embn)"
say "Please note: my GitHub handle is omatheusmesmo." >/dev/null   # near-duplicate of an existing fact
sleep "$SLEEP"
mid="$(embn)"
removed="$(review | grep -oE '[0-9]+' | head -1)"
after="$(embn)"
echo "  embeddings: before=$before afterStore=$mid removedByReview=$removed final=$after"
[ "${after:-9}" -le "${before:-0}" ] && ok "review removed near-duplicate(s) (final $after <= baseline $before)" \
  || bad "near-duplicate not collapsed (baseline $before, final $after)"

echo "############ 6. purge-all wipes everything ############"
purge
[ "$(embn)" = "0" ] && ok "purge-all -> 0 embeddings" || bad "purge-all left embeddings"

echo
echo "############ RESULT: $PASS passed, $FAIL failed ############"
if [ "$FAIL" -gt 0 ]; then printf '  failed: %s\n' "${FAILED[@]}"; exit 1; fi
