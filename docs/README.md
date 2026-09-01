# Workout Tracker documentation

How the app is put together and why, for whoever maintains it next.
Users want the root [`README.md`](../README.md); setup instructions are in
[`INSTALL.md`](../INSTALL.md).

## Pages

| Page | Covers |
|---|---|
| [`architecture.md`](./architecture.md) | Layers, package layout, and the screen/ViewModel/repository patterns every feature follows. |
| [`data-model.md`](./data-model.md) | Entities and their relationships, plus the weight, bodyweight and ordering semantics the schema does not show. |
| [`database.md`](./database.md) | Migrations, the destructive fallback that makes an unregistered migration destroy user data, and the version history. |
| [`building.md`](./building.md) | Toolchain, the uncommitted Gradle wrapper, JDK version traps, and getting the emulator to start. |
| [`testing.md`](./testing.md) | The extract-and-test pattern, what the suite covers, and a specific account of what it does not. |
| [`design-system.md`](./design-system.md) | The Trace visual system — tokens, type, the two-state accent rule, and why dynamic colour is gone. |
| [`json-format.md`](./json-format.md) | The program import/export schema, and why it is not a backup. |

## Which page do I update?

| Change | Page |
|---|---|
| Added or changed a Room entity | [`data-model.md`](./data-model.md) |
| Wrote a migration or bumped the database version | [`database.md`](./database.md) |
| Added a screen or a route | [`architecture.md`](./architecture.md) |
| Added a repository or moved logic between layers | [`architecture.md`](./architecture.md) |
| Changed how volume, prefill or bodyweight is calculated | [`data-model.md`](./data-model.md) |
| Extracted logic to make it testable, or added a test class | [`testing.md`](./testing.md) |
| Found something that cannot be tested automatically | [`testing.md`](./testing.md) |
| Changed the JSON schema or import behaviour | [`json-format.md`](./json-format.md) |
| Added a screen, or any visual change | [`design-system.md`](./design-system.md) |
| Changed a colour, type style or shape token | [`design-system.md`](./design-system.md) |
| Cut a release, or touched signing | [`building.md`](./building.md) |
| Hit a toolchain, Gradle or emulator problem worth not solving twice | [`building.md`](./building.md) |

## Note

`docs/superpowers/` holds design specs and implementation plans written
during development. It is process history, not reference documentation,
and is deliberately not indexed here.
