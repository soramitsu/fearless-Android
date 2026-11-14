package jp.co.soramitsu.common.resources

import android.content.ClipData
import android.content.ClipboardManager

private const val DEFAULT_LABEL = "fearless"

class ClipboardManager(
    private val clipboardManager: ClipboardManager,
    private val sensitive: SensitiveClipboard? = null
) {

    fun getFromClipboard(): String? = with(clipboardManager) {
        when {
            !hasPrimaryClip() -> null
            primaryClipDescription?.hasMimeType("text/*") != true -> null
            else -> primaryClip?.getItemAt(0)?.text.toString()
        }
    }

    fun addToClipboard(text: String, label: String = DEFAULT_LABEL) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
    }

    /** For seeds, phrases and keys: marked sensitive and cleared after a minute (see [SensitiveClipboard]). */
    fun addSecretToClipboard(text: String, label: String = DEFAULT_LABEL) {
        checkNotNull(sensitive) { "No sensitive clipboard configured" }.copy(label, text)
    }
}
