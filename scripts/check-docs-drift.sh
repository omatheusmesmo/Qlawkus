#!/usr/bin/env bash
# Fails when a generated documentation file differs from what is committed. Run it after the docs
# build (mvn -f docs/pom.xml verify): the build regenerates the Antora mirror, the config reference
# includes and both navigations, so any difference means someone edited a generated file by hand or
# changed site/content without committing the regenerated output.
# Usage: scripts/check-docs-drift.sh
set -euo pipefail

generated=(docs site/content/includes site/data)
changes="$(git status --porcelain --untracked-files=all -- "${generated[@]}")"

if [ -n "$changes" ]; then
  echo "Generated documentation files are out of date:"
  echo "$changes" | sed 's/^/  /'
  echo
  echo "Edit only site/content, then run: mvn -f docs/pom.xml verify -DskipTests"
  echo "and commit the regenerated files. Never edit docs/ or the generated includes by hand."
  git diff --stat -- "${generated[@]}" || true
  exit 1
fi
echo "Generated documentation files match the build output."
