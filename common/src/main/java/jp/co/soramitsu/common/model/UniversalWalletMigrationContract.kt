package jp.co.soramitsu.common.model

import com.google.gson.annotations.SerializedName

const val UNIVERSAL_WALLET_CUTOFF_AT_MILLIS: Long = 1_710_000_000_000L

data class UniversalWalletMigrationSnapshot(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = SCHEMA_VERSION,
    @SerializedName("platform")
    val platform: UniversalWalletMigrationPlatform,
    @SerializedName("hasUniversalWallet")
    val hasUniversalWallet: Boolean,
    @SerializedName("legacyVaults")
    val legacyVaults: List<UniversalWalletLegacyVaultDescriptor> = emptyList(),
    @SerializedName("cutoffAtMillis")
    val cutoffAtMillis: Long,
    @SerializedName("evaluatedAtMillis")
    val evaluatedAtMillis: Long
) {
    fun requiredAction(): UniversalWalletMigrationRequiredAction {
        return when {
            hasUniversalWallet -> UniversalWalletMigrationRequiredAction.NormalAccess
            legacyVaults.isNotEmpty() -> UniversalWalletMigrationRequiredAction.MigrateBeforeAccess
            else -> UniversalWalletMigrationRequiredAction.CreateUniversalWallet
        }
    }

    fun allowsNormalWalletAccess(): Boolean = requiredAction() == UniversalWalletMigrationRequiredAction.NormalAccess

    fun allowsLegacySecretExport(): Boolean = legacyVaults.any { it.canExportSecrets }

    fun validationErrors(): Set<UniversalWalletMigrationValidationError> {
        val errors = linkedSetOf<UniversalWalletMigrationValidationError>()

        if (schemaVersion != SCHEMA_VERSION) {
            errors += UniversalWalletMigrationValidationError.InvalidSchemaVersion
        }
        if (cutoffAtMillis <= 0 || evaluatedAtMillis <= 0) {
            errors += UniversalWalletMigrationValidationError.InvalidTimestamp
        }

        val vaultIds = mutableSetOf<String>()
        legacyVaults.forEach { vault ->
            errors += vault.validationErrors()
            if (!vaultIds.add(vault.vaultId)) {
                errors += UniversalWalletMigrationValidationError.DuplicateVaultId
            }
        }

        return errors
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class UniversalWalletLegacyVaultDescriptor(
    @SerializedName("vaultId")
    val vaultId: String,
    @SerializedName("accountId")
    val accountId: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("displayName")
    val displayName: String? = null,
    @SerializedName("mode")
    val mode: UniversalWalletLegacyVaultMode = UniversalWalletLegacyVaultMode.ExportOnly,
    @SerializedName("exportOnlyReason")
    val exportOnlyReason: String,
    @SerializedName("canExportSecrets")
    val canExportSecrets: Boolean = true,
    @SerializedName("canSignTransactions")
    val canSignTransactions: Boolean = false,
    @SerializedName("discoveredAtMillis")
    val discoveredAtMillis: Long,
    @SerializedName("lastExportedAtMillis")
    val lastExportedAtMillis: Long? = null
) {
    constructor(
        vaultId: String,
        accountId: String,
        ecosystem: UniversalWalletEcosystem,
        address: String,
        displayName: String? = null,
        mode: UniversalWalletLegacyVaultMode = UniversalWalletLegacyVaultMode.ExportOnly,
        exportOnlyReason: String,
        canExportSecrets: Boolean = true,
        canSignTransactions: Boolean = false,
        discoveredAtMillis: Long,
        lastExportedAtMillis: Long? = null
    ) : this(
        vaultId = vaultId,
        accountId = accountId,
        ecosystem = ecosystem.id,
        address = address,
        displayName = displayName,
        mode = mode,
        exportOnlyReason = exportOnlyReason,
        canExportSecrets = canExportSecrets,
        canSignTransactions = canSignTransactions,
        discoveredAtMillis = discoveredAtMillis,
        lastExportedAtMillis = lastExportedAtMillis
    )

    fun validationErrors(): Set<UniversalWalletMigrationValidationError> {
        val errors = linkedSetOf<UniversalWalletMigrationValidationError>()

        if (!UniversalWalletMigrationContractValidator.VAULT_ID.matches(vaultId)) {
            errors += UniversalWalletMigrationValidationError.InvalidVaultId
        }
        if (!UniversalWalletMigrationContractValidator.ACCOUNT_ID.matches(accountId)) {
            errors += UniversalWalletMigrationValidationError.InvalidAccountId
        }
        if (UniversalWalletEcosystem.fromId(ecosystem) == null) {
            errors += UniversalWalletMigrationValidationError.InvalidEcosystem
        }
        if (!UniversalWalletMigrationContractValidator.isMachineText(address, maxLength = 256)) {
            errors += UniversalWalletMigrationValidationError.InvalidAddress
        }
        if (displayName != null && !UniversalWalletMigrationContractValidator.isHumanText(displayName, maxLength = 64)) {
            errors += UniversalWalletMigrationValidationError.InvalidDisplayName
        }
        if (mode != UniversalWalletLegacyVaultMode.ExportOnly) {
            errors += UniversalWalletMigrationValidationError.InvalidLegacyMode
        }
        if (!UniversalWalletMigrationContractValidator.isHumanText(exportOnlyReason, maxLength = 160)) {
            errors += UniversalWalletMigrationValidationError.InvalidExportReason
        }
        if (!canExportSecrets) {
            errors += UniversalWalletMigrationValidationError.ExportDisabled
        }
        if (canSignTransactions) {
            errors += UniversalWalletMigrationValidationError.LegacySigningEnabled
        }
        if (discoveredAtMillis <= 0 || lastExportedAtMillis?.let { it < discoveredAtMillis } == true) {
            errors += UniversalWalletMigrationValidationError.InvalidTimestamp
        }

        return errors
    }
}

enum class UniversalWalletMigrationPlatform {
    @SerializedName("android")
    Android,

    @SerializedName("ios")
    Ios,

    @SerializedName("web")
    Web
}

enum class UniversalWalletMigrationRequiredAction {
    @SerializedName("normal-access")
    NormalAccess,

    @SerializedName("create-universal-wallet")
    CreateUniversalWallet,

    @SerializedName("migrate-before-access")
    MigrateBeforeAccess
}

enum class UniversalWalletLegacyVaultMode {
    @SerializedName("export-only")
    ExportOnly
}

enum class UniversalWalletMigrationValidationError {
    InvalidSchemaVersion,
    InvalidVaultId,
    DuplicateVaultId,
    InvalidAccountId,
    InvalidEcosystem,
    InvalidAddress,
    InvalidDisplayName,
    InvalidLegacyMode,
    InvalidExportReason,
    ExportDisabled,
    LegacySigningEnabled,
    InvalidTimestamp
}

object UniversalWalletMigrationContractValidator {
    val VAULT_ID = Regex("^legacy_[A-Za-z0-9_-]{8,64}$")
    val ACCOUNT_ID = Regex("^[a-z0-9][a-z0-9._:-]{1,63}$")

    fun isHumanText(value: String, maxLength: Int): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() &&
            normalized.length <= maxLength &&
            normalized.none { it.isISOControl() }
    }

    fun isMachineText(value: String, maxLength: Int): Boolean {
        return value.isNotEmpty() &&
            value.length <= maxLength &&
            value == value.trim() &&
            value.none { it <= ' ' || it.isISOControl() }
    }
}
