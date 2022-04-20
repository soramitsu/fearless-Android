package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.BuildConfig

data class AppVersion(val major: Int, val minor: Int, val buildNum: Int) {

    companion object {
        fun fromString(appVersionName: String): AppVersion {
            return appVersionName.split(".").let {
                AppVersion(
                    major = it[0].toIntOrNull() ?: 0,
                    minor = it[1].toIntOrNull() ?: 0,
                    buildNum = it[2].toIntOrNull() ?: 0
                )
            }
        }

        fun current(): AppVersion {
            return fromString(BuildConfig.APP_VERSION_NAME.split("-").first())
        }

        fun isSupported(versionText: String?): Boolean {
            return when (versionText) {
                null -> true
                else -> {
                    val appVersion = current()
                    val checkVersion = fromString(versionText)
                    !appVersion.before(checkVersion)
                }
            }
        }
    }
}

// "2.0.1".before("2.0.1") false
// "2.0.1".before("2.0.2") true
fun AppVersion.before(other: AppVersion) = when {
    major < other.major -> true
    major > other.major -> false
    minor < other.minor -> true
    minor > other.minor -> false
    else -> buildNum < other.buildNum
}

fun AppVersion.isSupportedByMinVersion(): Boolean {
    val appVersion = AppVersion.current()
    return !this.before(appVersion)
}
