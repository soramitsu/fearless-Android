package jp.co.soramitsu.common.view

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.accessibility.AccessibilityManager

/** Which apps, besides the user, can read a wallet screen through accessibility access. */
object AccessibilityExposure {
    data class Service(val label: String, val systemApp: Boolean)

    /**
     * Android 14+ hides screens marked with [protectSensitiveContent] from every service that is not
     * an accessibility tool, so only older versions need a warning. Services preinstalled in the
     * system image (TalkBack on most phones) are trusted like the rest of the system.
     */
    fun readersToWarnAbout(sdk: Int, services: List<Service>): List<String> =
        if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) emptyList()
        else services.filterNot(Service::systemApp).map(Service::label).distinct()

    fun readersToWarnAbout(context: Context): List<String> {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return emptyList()
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).mapNotNull { info ->
            val app = info.resolveInfo?.serviceInfo?.applicationInfo ?: return@mapNotNull null
            Service(app.loadLabel(context.packageManager).toString(), app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        }
        return readersToWarnAbout(Build.VERSION.SDK_INT, services)
    }
}
