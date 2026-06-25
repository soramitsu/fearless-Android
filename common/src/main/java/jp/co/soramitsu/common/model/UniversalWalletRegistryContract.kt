package jp.co.soramitsu.common.model

import com.google.gson.annotations.SerializedName

data class UniversalWalletChainRegistry(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = SCHEMA_VERSION,
    @SerializedName("chains")
    val chains: List<UniversalWalletChainRegistryEntry>
) {
    fun validationErrors(): Set<UniversalWalletRegistryValidationError> {
        val errors = linkedSetOf<UniversalWalletRegistryValidationError>()

        if (schemaVersion != SCHEMA_VERSION) {
            errors += UniversalWalletRegistryValidationError.InvalidSchemaVersion
        }
        if (chains.isEmpty()) {
            errors += UniversalWalletRegistryValidationError.ChainsRequired
        }

        val ids = mutableSetOf<String>()
        val chainIds = mutableSetOf<String>()
        chains.forEach { chain ->
            errors += chain.validationErrors()
            if (!ids.add(chain.id)) {
                errors += UniversalWalletRegistryValidationError.DuplicateChainId
            }
            if (!chainIds.add(chain.chainId)) {
                errors += UniversalWalletRegistryValidationError.DuplicateChainId
            }
        }

        return errors
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class UniversalWalletChainRegistryEntry(
    @SerializedName("id")
    val id: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("displayName")
    val displayName: String,
    @SerializedName("enabledByDefault")
    val enabledByDefault: Boolean,
    @SerializedName("nativeAsset")
    val nativeAsset: UniversalWalletRegistryAsset? = null,
    @SerializedName("derivationPath")
    val derivationPath: String? = null,
    @SerializedName("slip44CoinType")
    val slip44CoinType: Int? = null,
    @SerializedName("features")
    val features: List<String> = emptyList(),
    @SerializedName("endpoints")
    val endpoints: List<UniversalWalletRegistryEndpoint> = emptyList()
) {
    constructor(
        id: String,
        ecosystem: UniversalWalletEcosystem,
        chainId: String,
        displayName: String,
        enabledByDefault: Boolean,
        nativeAsset: UniversalWalletRegistryAsset? = null,
        derivationPath: String? = null,
        slip44CoinType: Int? = null,
        features: List<String> = emptyList(),
        endpoints: List<UniversalWalletRegistryEndpoint> = emptyList()
    ) : this(
        id = id,
        ecosystem = ecosystem.id,
        chainId = chainId,
        displayName = displayName,
        enabledByDefault = enabledByDefault,
        nativeAsset = nativeAsset,
        derivationPath = derivationPath,
        slip44CoinType = slip44CoinType,
        features = features,
        endpoints = endpoints
    )

    fun validationErrors(): Set<UniversalWalletRegistryValidationError> {
        val errors = linkedSetOf<UniversalWalletRegistryValidationError>()

        if (!UniversalWalletRegistryContractValidator.ID.matches(id)) {
            errors += UniversalWalletRegistryValidationError.InvalidId
        }
        if (UniversalWalletEcosystem.fromId(ecosystem) == null) {
            errors += UniversalWalletRegistryValidationError.InvalidEcosystem
        }
        if (!UniversalWalletRegistryContractValidator.CHAIN_ID.matches(chainId)) {
            errors += UniversalWalletRegistryValidationError.InvalidChainId
        }
        if (!UniversalWalletRegistryContractValidator.isHumanText(displayName, maxLength = 80)) {
            errors += UniversalWalletRegistryValidationError.InvalidDisplayName
        }
        if (enabledByDefault && endpoints.isEmpty()) {
            errors += UniversalWalletRegistryValidationError.EndpointRequired
        }
        if (nativeAsset != null) {
            errors += nativeAsset.validationErrors()
        }
        if (!derivationPath.isNullOrBlank() && !UniversalWalletRegistryContractValidator.DERIVATION_PATH.matches(derivationPath)) {
            errors += UniversalWalletRegistryValidationError.InvalidDerivationPath
        }
        if (slip44CoinType != null && slip44CoinType < 0) {
            errors += UniversalWalletRegistryValidationError.InvalidSlip44CoinType
        }

        val featureIds = mutableSetOf<String>()
        features.forEach { feature ->
            if (!UniversalWalletRegistryContractValidator.FEATURE_IDS.contains(feature)) {
                errors += UniversalWalletRegistryValidationError.InvalidFeatureId
            }
            if (!featureIds.add(feature)) {
                errors += UniversalWalletRegistryValidationError.DuplicateFeatureId
            }
        }

        val endpointIds = mutableSetOf<String>()
        endpoints.forEach { endpoint ->
            errors += endpoint.validationErrors()
            if (!endpointIds.add(endpoint.id)) {
                errors += UniversalWalletRegistryValidationError.DuplicateEndpointId
            }
        }

        return errors
    }
}

data class UniversalWalletRegistryAsset(
    @SerializedName("id")
    val id: String,
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("decimals")
    val decimals: Int,
    @SerializedName("name")
    val name: String? = null
) {
    fun validationErrors(): Set<UniversalWalletRegistryValidationError> {
        val errors = linkedSetOf<UniversalWalletRegistryValidationError>()

        if (!UniversalWalletRegistryContractValidator.ASSET_ID.matches(id)) {
            errors += UniversalWalletRegistryValidationError.InvalidAssetId
        }
        if (!UniversalWalletRegistryContractValidator.SYMBOL.matches(symbol)) {
            errors += UniversalWalletRegistryValidationError.InvalidAssetSymbol
        }
        if (decimals !in 0..255) {
            errors += UniversalWalletRegistryValidationError.InvalidAssetDecimals
        }
        if (name != null && !UniversalWalletRegistryContractValidator.isHumanText(name, maxLength = 80)) {
            errors += UniversalWalletRegistryValidationError.InvalidAssetName
        }

        return errors
    }
}

data class UniversalWalletRegistryEndpoint(
    @SerializedName("id")
    val id: String,
    @SerializedName("kind")
    val kind: UniversalWalletRegistryEndpointKind,
    @SerializedName("url")
    val url: String,
    @SerializedName("readOnly")
    val readOnly: Boolean,
    @SerializedName("priority")
    val priority: Int = 0
) {
    fun validationErrors(): Set<UniversalWalletRegistryValidationError> {
        val errors = linkedSetOf<UniversalWalletRegistryValidationError>()

        if (!UniversalWalletRegistryContractValidator.ID.matches(id)) {
            errors += UniversalWalletRegistryValidationError.InvalidEndpointId
        }
        if (!UniversalWalletRegistryContractValidator.isPublicOrLocalUrl(url)) {
            errors += UniversalWalletRegistryValidationError.InvalidEndpointUrl
        }
        if (priority < 0) {
            errors += UniversalWalletRegistryValidationError.InvalidEndpointPriority
        }
        if (!readOnly && kind == UniversalWalletRegistryEndpointKind.Indexer) {
            errors += UniversalWalletRegistryValidationError.PublicWriteIndexer
        }

        return errors
    }
}

enum class UniversalWalletRegistryEndpointKind {
    @SerializedName("indexer")
    Indexer,

    @SerializedName("rpc")
    Rpc,

    @SerializedName("torii-mcp")
    ToriiMcp,

    @SerializedName("explorer")
    Explorer
}

enum class UniversalWalletRegistryValidationError {
    InvalidSchemaVersion,
    ChainsRequired,
    DuplicateChainId,
    InvalidId,
    InvalidEcosystem,
    InvalidChainId,
    InvalidDisplayName,
    EndpointRequired,
    InvalidDerivationPath,
    InvalidSlip44CoinType,
    InvalidAssetId,
    InvalidAssetSymbol,
    InvalidAssetDecimals,
    InvalidAssetName,
    DuplicateEndpointId,
    InvalidEndpointId,
    InvalidEndpointUrl,
    InvalidEndpointPriority,
    DuplicateFeatureId,
    InvalidFeatureId,
    PublicWriteIndexer
}

object UniversalWalletRegistryContractValidator {
    val ID = Regex("^[a-z0-9][a-z0-9._:-]{1,63}$")
    val CHAIN_ID = Regex("^[A-Za-z0-9._:-]{2,128}$")
    val ASSET_ID = Regex("^[A-Za-z0-9._:-]{1,128}$")
    val SYMBOL = Regex("^[A-Z0-9]{2,16}$")
    val DERIVATION_PATH = Regex("^m(?:/[0-9]+'?)*$")
    val FEATURE_IDS = setOf("transfer", "offline-cash", "sccp", "governance")
    private val PUBLIC_OR_LOCAL_URL = Regex("^(https://[^\\s]+|http://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]+)?(?:/[^\\s]*)?)$")

    fun isPublicOrLocalUrl(value: String): Boolean = PUBLIC_OR_LOCAL_URL.matches(value)

    fun isHumanText(value: String, maxLength: Int): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() &&
            normalized.length <= maxLength &&
            normalized.none { it.isISOControl() }
    }
}
