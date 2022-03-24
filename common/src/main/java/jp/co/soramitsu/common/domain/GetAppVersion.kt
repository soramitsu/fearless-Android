package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.resources.ContextManager

class GetAppVersion(private val contextManager: ContextManager) {
    operator fun invoke(): AppVersion = contextManager.getContext().let {
        val components = it.packageManager.getPackageInfo(it.packageName, 0)
            .versionName
            .split("-")
            .first()
            .split(".")

        AppVersion(components[0].toInt(), components[1].toInt(), components[2].toInt())
    }
}

data class AppVersion(val major: Int, val minor: Int, val buildNum: Int) {

    companion object {
        fun fromString(appVersionName: String): AppVersion {
            return appVersionName.split(".").let {
                AppVersion(
                    it[0].toInt(),
                    it[1].toInt(),
                    it[2].toInt()
                )
            }
        }
    }
}

fun isAppVersionSupported(minSupportedVersionName: String?, appVersion: AppVersion?): Boolean {
    minSupportedVersionName ?: return true
    requireNotNull(appVersion)

    val minSupportedVersion = AppVersion.fromString(minSupportedVersionName)

    return when {
        minSupportedVersion.major > appVersion.major -> false
        minSupportedVersion.minor > appVersion.minor -> false
        minSupportedVersion.buildNum > appVersion.buildNum -> false
        else -> true
    }
}
