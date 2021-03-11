package jp.co.soramitsu.common.resources

import android.content.ClipData
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager

private const val DEFAULT_LABEL = "fearless"

class ClipboardManager(
    private val clipboardManager: ClipboardManager
) {

    fun getFromClipboard(): String? {
        return with(clipboardManager) {
            if (!hasPrimaryClip()) {
                null
            } else if (!primaryClipDescription!!.hasMimeType(MIMETYPE_TEXT_PLAIN)) {
                null
            } else {
                val item: ClipData.Item = primaryClip!!.getItemAt(0)

                item.text.toString()
            }
        }
    }

    fun addToClipboard(text: String, label: String = DEFAULT_LABEL) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
    }
}