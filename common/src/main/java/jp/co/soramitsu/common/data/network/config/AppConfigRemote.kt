package jp.co.soramitsu.common.data.network.config

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

data class AppConfigRemote(
    @SerializedName("min_supported_version")
    val minSupportedVersion: String,
    @SerializedName("exсluded_versions")
    val excludedVersions: List<String>
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
