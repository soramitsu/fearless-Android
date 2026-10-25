# Native Android source qualification

Android owns its runtime utilities, guarded transport, platform modules and remote configuration in this checkout. Public ancestry preserves the previous master, develop and recovery branch; the imported source snapshot does not publish private development history.

Historical JSON review and validation receipts are retained in the local migration recovery archive. References to them in older roadmaps describe past qualification, not approval of this source revision. Source, tests, licenses, binary provenance and release guards remain in this repository.

The native consolidation was checked with `runTest`, Debug APK assembly, Debug lint and Detekt: 6,429 host methods, 6,411 passed and 18 inherited or opt-in skips. The subsequent recovery change was checked separately across six modules: 3,478 methods, 3,464 passed and 14 inherited skips. All 915 account methods passed; Debug assembly, Detekt and account AndroidTest compilation passed. The canonical Ed25519 verification regression is retained in runtime-utils and runs in its unit suite.

Device tests were not run for this migration. Device migrations, production signing, live backup services, funded XCM and store distribution still require their existing release checks. Production XCM remains disabled without approved route evidence. Configuration URLs must be present on public master before shipping, and older installed applications still require their legacy configuration hosts.

GitHub CI and independent code-owner approval are required before master integration. This document records source validation, not permission to distribute an application.
