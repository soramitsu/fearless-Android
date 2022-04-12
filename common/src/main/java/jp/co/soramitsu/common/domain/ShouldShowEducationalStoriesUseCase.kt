package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.data.storage.Preferences
import kotlin.reflect.KProperty

class ShouldShowEducationalStoriesUseCase(private val preferences: Preferences) {

    private val key = "shouldShowEducationalStories"

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        return preferences.getBoolean(key, true)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        preferences.putBoolean(key, value)
    }
}
