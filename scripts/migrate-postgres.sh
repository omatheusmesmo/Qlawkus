#!/usr/bin/env bash
# Moves the Qlawkus databases from a PostgreSQL 17 volume to the PostgreSQL 19 one.
#
# Why a dump and not a tag bump: a major version cannot read another's data files, and the
# 18+ images keep the cluster in /var/lib/postgresql/<major>/docker, so the old volume would
# sit next to a new, empty cluster and the agent would boot with no memory.
#
# The old volume is only read (a temporary postgres:17 container starts on it, dumps every
# database in custom format, and stops) and is never removed: it is the rollback. The dumps stay
# in $DUMP_DIR as a second copy. Stop the stack first: ./run.sh <local|prod|native> down
#
# Usage: scripts/migrate-postgres.sh   (or ./run.sh migrate-postgres)
# Env:   OLD_VOLUME (qlawkus_pgdata)  NEW_VOLUME (qlawkus_postgres)
#        DUMP_DIR (${XDG_STATE_HOME:-~/.local/state}/qlawkus/pg-migration-<timestamp>, outside the
#                  repository so a dump is never committed by accident)
#        OLD_IMAGE (pgvector/pgvector:pg17)  NEW_IMAGE (pgvector/pgvector:0.8.7-pg19, as the compose files)
#        POSTGRES_USER / POSTGRES_PASSWORD (qlawkus / qlawkus, as the compose files)
set -euo pipefail

OLD_VOLUME="${OLD_VOLUME:-qlawkus_pgdata}"
NEW_VOLUME="${NEW_VOLUME:-qlawkus_postgres}"
DUMP_DIR="${DUMP_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/qlawkus/pg-migration-$(date +%Y%m%d-%H%M%S)}"
PGUSER_NAME="${POSTGRES_USER:-qlawkus}"
PGPASS="${POSTGRES_PASSWORD:-qlawkus}"
OLD_IMAGE="${OLD_IMAGE:-pgvector/pgvector:pg17}"
NEW_IMAGE="${NEW_IMAGE:-pgvector/pgvector:0.8.7-pg19}"
OLD_CT="qlawkus-pg-migrate-old-$$"
NEW_CT="qlawkus-pg-migrate-new-$$"
created_new_volume=0
finished=0

die() { echo "error: $*" >&2; exit 1; }

cleanup() {
  local code=$?
  docker rm -f "$OLD_CT" "$NEW_CT" >/dev/null 2>&1 || true
  if [ "$finished" = 0 ] && [ "$created_new_volume" = 1 ]; then
    docker volume rm "$NEW_VOLUME" >/dev/null 2>&1 \
      && echo "removed the unfinished $NEW_VOLUME (created by this run); $OLD_VOLUME was not touched" >&2
  fi
  exit "$code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

in_use_by() {
  docker ps --filter "volume=$1" --format '{{.Names}}'
}

docker volume inspect "$OLD_VOLUME" >/dev/null 2>&1 || die "volume $OLD_VOLUME does not exist"
if docker volume inspect "$NEW_VOLUME" >/dev/null 2>&1; then
  die "volume $NEW_VOLUME already exists; if it is a failed attempt, remove it first (docker volume rm $NEW_VOLUME)"
fi
for volume in "$OLD_VOLUME" "$NEW_VOLUME"; do
  users=$(in_use_by "$volume")
  [ -z "$users" ] || die "container(s) still using $volume: $(echo "$users" | tr '\n' ' '); stop the stack first (./run.sh <local|prod|native> down)"
done

old_version=$(docker run --rm --entrypoint cat -v "$OLD_VOLUME:/v:ro" "$OLD_IMAGE" /v/PG_VERSION 2>/dev/null || true)
[ -n "$old_version" ] || die "$OLD_VOLUME holds no PostgreSQL cluster at its root (no PG_VERSION)"
old_major="${OLD_IMAGE##*pg}"
case "$old_major" in
  ''|*[!0-9]*) ;;
  *) [ "$old_version" = "$old_major" ] || die "$OLD_VOLUME is PostgreSQL $old_version but OLD_IMAGE is $OLD_IMAGE; set OLD_IMAGE to the matching pgvector tag" ;;
esac

wait_ready() {
  for _ in $(seq 1 60); do
    docker exec "$1" pg_isready -U "$PGUSER_NAME" -d postgres >/dev/null 2>&1 && return 0
    sleep 1
  done
  docker logs --tail 20 "$1" >&2 || true
  die "$1 did not become ready"
}

row_counts() {
  docker exec -e PGPASSWORD="$PGPASS" "$1" psql -U "$PGUSER_NAME" -d "$2" -Atc \
    "select table_name || '=' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text
       from information_schema.tables where table_schema not in ('pg_catalog','information_schema') and table_type = 'BASE TABLE' order by 1"
}

umask 077
mkdir -p "$DUMP_DIR"
echo "== dumping from $OLD_VOLUME ($OLD_IMAGE) into $DUMP_DIR"
docker run -d --name "$OLD_CT" -e POSTGRES_USER="$PGUSER_NAME" -e POSTGRES_PASSWORD="$PGPASS" \
  -v "$OLD_VOLUME:/var/lib/postgresql/data" "$OLD_IMAGE" >/dev/null
wait_ready "$OLD_CT"

mapfile -t DATABASES < <(docker exec "$OLD_CT" psql -U "$PGUSER_NAME" -d postgres -Atc \
  "select datname from pg_database where not datistemplate and datname <> 'postgres' order by 1")
[ "${#DATABASES[@]}" -gt 0 ] || die "no application database found in $OLD_VOLUME"

for db in "${DATABASES[@]}"; do
  echo "   dump $db"
  docker exec "$OLD_CT" pg_dump -U "$PGUSER_NAME" -Fc "$db" > "$DUMP_DIR/$db.dump"
  [ -s "$DUMP_DIR/$db.dump" ] || die "dump of $db is empty"
  row_counts "$OLD_CT" "$db" > "$DUMP_DIR/$db.counts.before"
done
docker rm -f "$OLD_CT" >/dev/null

echo "== restoring into $NEW_VOLUME ($NEW_IMAGE)"
created_new_volume=1
docker run -d --name "$NEW_CT" -e POSTGRES_USER="$PGUSER_NAME" -e POSTGRES_PASSWORD="$PGPASS" \
  -v "$NEW_VOLUME:/var/lib/postgresql" "$NEW_IMAGE" >/dev/null
wait_ready "$NEW_CT"

for db in "${DATABASES[@]}"; do
  echo "   restore $db"
  if [ -z "$(docker exec "$NEW_CT" psql -U "$PGUSER_NAME" -d postgres -Atc "select 1 from pg_database where datname = '$db'")" ]; then
    docker exec "$NEW_CT" psql -U "$PGUSER_NAME" -d postgres -qc "create database \"$db\"" >/dev/null
  fi
  docker exec -i "$NEW_CT" pg_restore -U "$PGUSER_NAME" -d "$db" --exit-on-error --no-owner < "$DUMP_DIR/$db.dump"
  row_counts "$NEW_CT" "$db" > "$DUMP_DIR/$db.counts.after"
  if ! diff -q "$DUMP_DIR/$db.counts.before" "$DUMP_DIR/$db.counts.after" >/dev/null; then
    diff "$DUMP_DIR/$db.counts.before" "$DUMP_DIR/$db.counts.after" || true
    die "row counts differ for $db; $NEW_VOLUME is not trustworthy, $OLD_VOLUME is untouched"
  fi
done

docker stop "$NEW_CT" >/dev/null
finished=1

echo "== done: ${#DATABASES[@]} database(s) moved, row counts identical in every table"
for db in "${DATABASES[@]}"; do
  echo "   $db: $(wc -l < "$DUMP_DIR/$db.counts.after") table(s), dump $(du -h "$DUMP_DIR/$db.dump" | cut -f1)"
done
echo "   new volume : $NEW_VOLUME (PostgreSQL 19)"
echo "   old volume : $OLD_VOLUME kept untouched; it is the rollback (restore the previous compose files and start on it)"
echo "   dumps      : $DUMP_DIR"
echo "   next       : ./run.sh <local|prod|native> up"
