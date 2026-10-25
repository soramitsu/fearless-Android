package jp.co.soramitsu.common.compose.theme.fw

import android.content.Context
import android.content.SharedPreferences

/** Ordinary display preferences, isolated from wallet and authentication stores. */
class FwAppearancePreferences internal constructor(internal val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE))

    fun read(): FwAppearance {
        val values = preferences.all
        val choice = FwTierChoice.entries.firstOrNull { it.name == values[CHOICE] } ?: FwTierChoice.Automatic
        return FwAppearance(choice, values[CONTRAST] as? Boolean ?: false)
    }

    /** Reports persistence failure so the Appearance UI can show an error and offer retry. */
    fun save(appearance: FwAppearance): Boolean = preferences.edit()
        .putString(CHOICE, appearance.choice.name)
        .putBoolean(CONTRAST, appearance.highContrast)
        .commit()

    companion object {
        const val STORE = "fw_appearance"
        internal const val CHOICE = "visual_effects"
        internal const val CONTRAST = "increase_contrast"
    }
}
