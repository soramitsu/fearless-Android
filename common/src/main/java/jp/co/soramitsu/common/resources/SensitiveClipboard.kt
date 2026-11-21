package jp.co.soramitsu.common.resources

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.security.MessageDigest

/**
 * Copies secret text (a phrase, a seed, a private key, retained wallet data) so that keyboards and
 * clipboard previews treat it as sensitive, and clears it again after [LIFETIME_MS].
 *
 * Only the newest secret copy is cleared, and only while the clipboard still holds it when the
 * app can read the clipboard. In the background Android 10+ hides the clipboard from apps; then
 * it is cleared anyway, as password managers do.
 */
class SensitiveClipboard internal constructor(
    private val port: Port,
    private val schedule: (delayMillis: Long, action: () -> Unit) -> Unit
) {
    /** The platform clipboard, behind a seam the unit tests replace. */
    interface Port {
        fun copySensitive(label: String, text: String)
        /** The clipboard's text, or null when the platform hides it from this app. */
        fun currentText(): String?
        fun clear()
    }

    private var generation = 0L

    fun copy(label: String, text: String) {
        val digest = digest(text)
        val mine = synchronized(this) { ++generation }
        port.copySensitive(label, text)
        schedule(LIFETIME_MS) { clearIfStillOurs(mine, digest) }
    }

    private fun clearIfStillOurs(mine: Long, digest: ByteArray) {
        if (synchronized(this) { mine != generation }) return
        val current = runCatching { port.currentText() }.getOrNull()
        if (current != null && !MessageDigest.isEqual(digest(current), digest)) return
        runCatching { port.clear() }
    }

    private fun digest(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

    companion object {
        const val LIFETIME_MS = 60_000L
        private const val LEGACY_SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"

        @Volatile private var shared: SensitiveClipboard? = null

        /** The process-wide instance; its timer outlives the screen that copied. */
        fun get(context: Context): SensitiveClipboard = shared ?: synchronized(this) {
            shared ?: create(context.applicationContext).also { shared = it }
        }

        private fun create(context: Context): SensitiveClipboard {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            val handler = Handler(Looper.getMainLooper())
            return SensitiveClipboard(PlatformPort(clipboard)) { delay, action -> handler.postDelayed({ action() }, delay) }
        }
    }

    private class PlatformPort(private val clipboard: android.content.ClipboardManager?) : Port {
        override fun copySensitive(label: String, text: String) {
            val manager = clipboard ?: return
            val clip = ClipData.newPlainText(label, text)
            clip.description.extras = PersistableBundle().apply {
                // Keeps the value out of the keyboard's clipboard preview.
                putBoolean(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE
                    else LEGACY_SENSITIVE_EXTRA,
                    true
                )
            }
            manager.setPrimaryClip(clip)
        }

        override fun currentText(): String? = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

        override fun clear() {
            val manager = clipboard ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.clearPrimaryClip()
            else manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
