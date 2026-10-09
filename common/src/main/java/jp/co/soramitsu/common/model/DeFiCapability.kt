package jp.co.soramitsu.common.model

enum class DeFiAvailability {
    Checking,
    Available,
    SetupRequired,
    Unavailable
}

enum class DeFiAccountRequirement {
    None,
    AnySupported,
    Sora
}

enum class DeFiSigningRequirement {
    None,
    SignableAccount
}

/**
 * Stable cross-feature capability contract for DeFi destinations and their mutations.
 *
 * A destination remains navigable when [actionsEnabled] is false. Callers gate only transaction
 * actions with [canPerformAction] and show [userFacingReason] as an explicit setup/availability
 * state instead of removing the feature.
 */
data class DeFiCapability(
    val featureId: String,
    val availability: DeFiAvailability,
    val accountRequirement: DeFiAccountRequirement,
    val signingRequirement: DeFiSigningRequirement,
    val supportedNetworkIds: Set<String>,
    val destinationEnabled: Boolean,
    val actionsEnabled: Boolean,
    val userFacingReason: String? = null
) {
    init {
        require(featureId.isNotBlank()) { "DeFi feature id must not be blank" }
        require(availability == DeFiAvailability.Available || userFacingReason != null) {
            "Unavailable DeFi capability must explain why"
        }
        require(actionsEnabled || userFacingReason != null) {
            "Disabled DeFi actions must explain why"
        }
    }

    val canOpenDestination: Boolean get() = destinationEnabled
    val canPerformAction: Boolean
        get() = availability == DeFiAvailability.Available && actionsEnabled

    companion object {
        fun checking(featureId: String): DeFiCapability = DeFiCapability(
            featureId = featureId,
            availability = DeFiAvailability.Checking,
            accountRequirement = DeFiAccountRequirement.AnySupported,
            signingRequirement = DeFiSigningRequirement.SignableAccount,
            supportedNetworkIds = emptySet(),
            destinationEnabled = false,
            actionsEnabled = false,
            userFacingReason = "Checking capability…"
        )
    }
}
