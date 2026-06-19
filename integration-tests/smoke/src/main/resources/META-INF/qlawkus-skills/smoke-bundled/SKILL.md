---
name: smoke-bundled
description: "A bundled skill shipped on the classpath, discovered at build time."
---

# Smoke Bundled Skill

This skill exists to verify that the build-time bundled-skill discovery pipeline
finds and bakes classpath `META-INF/qlawkus-skills/**/SKILL.md` resources.

1. Confirm the build step logged it.
2. Confirm it appears in the injected skill index.
