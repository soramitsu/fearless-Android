package jp.co.soramitsu.common.data.network.config

import com.google.gson.annotations.SerializedName

data class AppConfigRemote(
    @SerializedName("min_supported_version")
    val minSupportedVersion: String,
    @SerializedName("exсluded_versions")
    val excludedVersions: List<String>
)
