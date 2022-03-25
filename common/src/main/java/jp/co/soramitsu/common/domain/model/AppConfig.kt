package jp.co.soramitsu.common.domain.model

import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.domain.before

data class AppConfig(
    val minSupportedVersion: AppVersion,
    val excludedVersions: List<AppVersion>
) {
    val isCurrentVersionSupported: Boolean
        get() {
            val appVersion = AppVersion.current()
            val excluded = excludedVersions.contains(appVersion)
            val beforeMin = appVersion.before(minSupportedVersion)
            return excluded.not() && beforeMin.not()
        }
}

fun AppConfigRemote.toDomain() = AppConfig(AppVersion.fromString(minSupportedVersion), excludedVersions.map(AppVersion::fromString))
