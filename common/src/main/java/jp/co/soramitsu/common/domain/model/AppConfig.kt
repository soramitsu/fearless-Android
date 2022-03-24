package jp.co.soramitsu.common.domain.model

import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.domain.AppVersion

data class AppConfig(
    val minSupportedVersion: AppVersion,
    val excludedVersions: List<AppVersion>
)

fun AppConfigRemote.toDomain() = AppConfig(AppVersion.fromString(minSupportedVersion), excludedVersions.map(AppVersion::fromString))
