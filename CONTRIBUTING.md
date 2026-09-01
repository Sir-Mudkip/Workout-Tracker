# Contributing

This repository is a local-only Android workout tracker.

Start with [`docs/README.md`](docs/README.md) for the documentation index,
and [`CLAUDE.md`](CLAUDE.md) (also `AGENTS.md`) for the rules that govern
this codebase.

Build and test with `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`
— see the Build section of [`CLAUDE.md`](CLAUDE.md) for the toolchain caveats,
including the uncommitted Gradle wrapper and the JDK version requirement.

**Anything touching the database schema needs manual verification.** Install
over an existing install and confirm prior data survives; a fresh install never
runs the migration. [`docs/database.md`](docs/database.md) explains why this
matters more here than in most projects.

Commits follow Conventional Commits. AI agents disclose themselves with an
`Assisted-by:` footer on every commit.
