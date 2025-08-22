# AGENTS Guide: common

Purpose
- Shared UI base classes, error handling, utilities, and small domain helpers reused across features.

Key Areas
- Base UI: `base/*` (e.g., `BaseFragment`, `BaseComposeFragment`, `BaseViewModel`).
- Errors: `base/errors/*` including `FearlessException`, `ValidationException`, `TitledException`.
- Validation: `validation/*` (`Validation`, `ValidationExecutor`).
- Storage/Encryption: `data/storage/*` with `EncryptedPreferences` and `PreferencesImpl`.
- Utilities: `utils/*` (e.g., `Base58Ext`, `TonUtils`, coroutines helpers), QR scanning in `qrScanner/*`.

Common Tasks
- Add a reusable bottom sheet or view: place in `view/*` and keep dependencies minimal.
- Add a validation: define a `Validation` and wire with `ValidationExecutor` in ViewModels.
- Add a utility: prefer extension functions; keep platform-specific code isolated.

Known TODOs/Tech Debt
- `base/errors/FearlessException.kt`: replace TODOs with localized string resources.
- `data/storage/PreferencesImpl.kt`: note about listener GC — consider rework.
- `data/network/rpc/SocketSingleRequestExecutor.kt`: marked unused; confirm and remove if truly dead.
- `utils/Base58Ext.kt`: consider moving Base58 to `fearless-utils`.

Tests & Checks
- Use `:test-shared` utilities where applicable.
- Run: `./gradlew :common:testDebugUnitTest` and `./gradlew detektAll`.

