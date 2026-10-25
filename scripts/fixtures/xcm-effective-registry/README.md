# Effective registry audit fixtures

Frozen copies of the bundled XCM registry as it was before the 2026-10-23 retirement of every route
(`runtime/src/main/assets/local_chains.json`, `runtime/src/main/assets/approved_xcm_routes.tsv`,
`scripts/xcm-required-routes.tsv`).

They are **test input only**. `scripts/test-xcm-effective-registry-audit.sh` mutates them to prove that
`scripts/audit-xcm-effective-registry.sh` rejects a drifted, widened or injected registry, and that needs a registry
with executable routes while the real one has none. The execution specs inside describe routes that are rejected on
chain today (relay reserve transfers since the Asset Hub Migration, destinations that are no longer registered), so
nothing here is authority, and no production code or asset may read this directory.
