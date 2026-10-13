package jp.co.soramitsu.common.data.network.config

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

data class AppConfigRemote(
    @SerializedName("min_supported_version")
    val minSupportedVersion: String,
    @SerializedName("excluded_versions")
    val excludedVersions: List<String>?
)

data class PolkaswapRemoteConfig(
    @SerializedName("version")
    val version: String,
    @SerializedName("availableDexIds")
    val availableDexIds: List<AvailableDexId>,
    @SerializedName("availableSources")
    val availableSources: List<String>,
    @SerializedName("forceSmartIds")
    val forceSmartIds: List<String>,
    @SerializedName("xstusdId")
    val xstusdId: String
)

data class AvailableDexId(
    @SerializedName("name")
    val name: String,
    @SerializedName("code")
    val code: BigInteger,
    @SerializedName("assetId")
    val assetId: String
)


data class FeatureToggleConfig(
    @SerializedName("pendulum_case_enabled")
    val pendulumCaseEnabled: Boolean = false,
    @SerializedName("asset_discovery_shadow_enabled")
    val assetDiscoveryShadowEnabled: Boolean? = null,
    @SerializedName("portfolio_navigation_enabled")
    val portfolioNavigationEnabled: Boolean? = null,
    @SerializedName("polkaswap_mutations_enabled")
    val polkaswapMutationsEnabled: Boolean? = null,
    @SerializedName("demeter_mutations_enabled")
    val demeterMutationsEnabled: Boolean? = null,
    @SerializedName("polkamarkt_mutations_enabled")
    val polkamarktMutationsEnabled: Boolean? = null,
    @SerializedName("xcm_mutations_enabled")
    val xcmMutationsEnabled: Boolean? = null,
    @SerializedName("polkaswap_bridge_mutations_enabled")
    val polkaswapBridgeMutationsEnabled: Boolean? = null,
    @SerializedName("mutation_authorization")
    val mutationAuthorization: String? = null,
)
