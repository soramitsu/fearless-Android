package jp.co.soramitsu.common.model

import com.google.gson.annotations.SerializedName

data class UniversalWalletIdentity(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = SCHEMA_VERSION,
    @SerializedName("walletId")
    val walletId: String,
    @SerializedName("displayName")
    val displayName: String,
    @SerializedName("source")
    val source: UniversalWalletSource,
    @SerializedName("status")
    val status: UniversalWalletStatus,
    @SerializedName("publicAccounts")
    val publicAccounts: List<UniversalWalletPublicAccount>,
    @SerializedName("createdAtMillis")
    val createdAtMillis: Long,
    @SerializedName("updatedAtMillis")
    val updatedAtMillis: Long? = null,
    @SerializedName("legacyExportOnlyReason")
    val legacyExportOnlyReason: String? = null
) {
    fun validationErrors(): Set<ValidationError> {
        val errors = linkedSetOf<ValidationError>()

        if (schemaVersion != SCHEMA_VERSION) {
            errors += ValidationError.InvalidSchemaVersion
        }
        if (!WALLET_ID.matches(walletId)) {
            errors += ValidationError.InvalidWalletId
        }
        if (!isHumanText(displayName, maxLength = 64)) {
            errors += ValidationError.InvalidDisplayName
        }
        if (createdAtMillis <= 0 || updatedAtMillis?.let { it < createdAtMillis } == true) {
            errors += ValidationError.InvalidTimestamps
        }

        if (status != UniversalWalletStatus.LegacyExportOnly && publicAccounts.isEmpty()) {
            errors += ValidationError.PublicAccountsRequired
        }
        if (status == UniversalWalletStatus.Active) {
            val present = publicAccounts.mapNotNull { UniversalWalletEcosystem.fromId(it.ecosystem) }.toSet()
            if (present != UniversalWalletEcosystem.values().toSet()) {
                errors += ValidationError.MissingActiveEcosystem
            }
        }
        if (status == UniversalWalletStatus.LegacyExportOnly) {
            if (source != UniversalWalletSource.LegacyImport) {
                errors += ValidationError.InvalidLegacySource
            }
            if (!isHumanText(legacyExportOnlyReason, maxLength = 160)) {
                errors += ValidationError.LegacyReasonRequired
            }
        } else if (!legacyExportOnlyReason.isNullOrBlank()) {
            errors += ValidationError.LegacyReasonNotAllowed
        }

        val accountIds = mutableSetOf<String>()
        publicAccounts.forEach { account ->
            errors += account.validationErrors()
            if (!accountIds.add(account.accountId)) {
                errors += ValidationError.DuplicateAccountId
            }
        }

        return errors
    }

    fun requireValid(): UniversalWalletIdentity {
        val errors = validationErrors()
        if (errors.isNotEmpty()) {
            throw UniversalWalletIdentityException(errors)
        }

        return this
    }

    data class UniversalWalletPublicAccount(
        @SerializedName("accountId")
        val accountId: String,
        @SerializedName("ecosystem")
        val ecosystem: String,
        @SerializedName("address")
        val address: String,
        @SerializedName("chainId")
        val chainId: String? = null,
        @SerializedName("derivationPath")
        val derivationPath: String? = null,
        @SerializedName("publicKeyHex")
        val publicKeyHex: String? = null,
        @SerializedName("isDefault")
        val isDefault: Boolean = true
    ) {
        constructor(
            accountId: String,
            ecosystem: UniversalWalletEcosystem,
            address: String,
            chainId: String? = null,
            derivationPath: String? = null,
            publicKeyHex: String? = null,
            isDefault: Boolean = true
        ) : this(
            accountId = accountId,
            ecosystem = ecosystem.id,
            address = address,
            chainId = chainId,
            derivationPath = derivationPath,
            publicKeyHex = publicKeyHex,
            isDefault = isDefault
        )

        fun validationErrors(): Set<ValidationError> {
            val errors = linkedSetOf<ValidationError>()

            if (!ACCOUNT_ID.matches(accountId)) {
                errors += ValidationError.InvalidAccountId
            }
            if (UniversalWalletEcosystem.fromId(ecosystem) == null) {
                errors += ValidationError.InvalidEcosystem
            }
            if (!isMachineText(address, maxLength = 256)) {
                errors += ValidationError.InvalidAddress
            }
            if (chainId != null && !CHAIN_ID.matches(chainId)) {
                errors += ValidationError.InvalidChainId
            }
            if (!derivationPath.isNullOrBlank() && !DERIVATION_PATH.matches(derivationPath)) {
                errors += ValidationError.InvalidDerivationPath
            }
            if (publicKeyHex != null && (!PUBLIC_KEY_HEX.matches(publicKeyHex) || publicKeyHex.length % 2 != 0)) {
                errors += ValidationError.InvalidPublicKeyHex
            }

            return errors
        }
    }

    enum class UniversalWalletSource {
        @SerializedName("created-24-word")
        Created24Word,

        @SerializedName("imported-12-word")
        Imported12Word,

        @SerializedName("imported-24-word")
        Imported24Word,

        @SerializedName("legacy-import")
        LegacyImport
    }

    enum class UniversalWalletStatus {
        @SerializedName("active")
        Active,

        @SerializedName("migration-required")
        MigrationRequired,

        @SerializedName("legacy-export-only")
        LegacyExportOnly
    }

    enum class ValidationError {
        InvalidSchemaVersion,
        InvalidWalletId,
        InvalidDisplayName,
        InvalidTimestamps,
        PublicAccountsRequired,
        MissingActiveEcosystem,
        InvalidLegacySource,
        LegacyReasonRequired,
        LegacyReasonNotAllowed,
        DuplicateAccountId,
        InvalidAccountId,
        InvalidEcosystem,
        InvalidAddress,
        InvalidChainId,
        InvalidDerivationPath,
        InvalidPublicKeyHex
    }

    class UniversalWalletIdentityException(
        val errors: Set<ValidationError>
    ) : IllegalArgumentException(errors.joinToString(",") { it.name })

    companion object {
        const val SCHEMA_VERSION = 2

        private val WALLET_ID = Regex("^uw2_[A-Za-z0-9_-]{16,64}$")
        private val ACCOUNT_ID = Regex("^[a-z0-9][a-z0-9._:-]{1,63}$")
        private val CHAIN_ID = Regex("^[A-Za-z0-9._:-]{2,128}$")
        private val DERIVATION_PATH = Regex("^m(?:/[0-9]+'?)*$")
        private val PUBLIC_KEY_HEX = Regex("^[0-9a-f]{64,260}$")

        private fun isHumanText(value: String?, maxLength: Int): Boolean {
            val normalized = value?.trim() ?: return false
            return normalized.isNotEmpty() &&
                normalized.length <= maxLength &&
                normalized.none { it.isISOControl() }
        }

        private fun isMachineText(value: String, maxLength: Int): Boolean {
            return value.isNotEmpty() &&
                value.length <= maxLength &&
                value == value.trim() &&
                value.none { it <= ' ' || it.isISOControl() }
        }
    }
}
