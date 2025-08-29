# AGENTS Guide: core-db

Purpose
- Central Room database with entities, DAOs, and migrations for chains, assets, accounts, operations, and integrations.

Key Files
- `AppDatabase.kt` — database definition, versioning, and type converters.
- Entities: `model/*` and `model/chain/*` (e.g., `ChainLocal`, `ChainAssetLocal`, `MetaAccountLocal`).
- DAOs: `dao/*` (e.g., `ChainDao`, `AssetDao`, `MetaAccountDao`).
- Migrations: `migrations/*` with helpers for version-to-version upgrades.
- Schemas: `schemas/` for Room schema snapshots (used by migration tests).

Common Tasks
- Add a table/column:
  1) Add/update entity model(s) with Room annotations.
  2) Increment DB version in `AppDatabase`.
  3) Add a migration under `migrations/` and wire it in `Migrations.kt`.
  4) Update DAOs.
  5) Write a migration test.

Migration Testing
- Pattern: use Room’s auto-migration/migration test harness.
- Run: `./gradlew :core-db:testDebugUnitTest`.

Integration
- `runtime` consumes `ChainDao` for chain info, nodes, and sync decisions.
- Feature modules query/update asset/account data via DAOs.

Notes
- Keep converters focused; large JSON fields should be carefully versioned.
- Ensure `@Transaction` is used for multi-DAO updates where consistency matters.

