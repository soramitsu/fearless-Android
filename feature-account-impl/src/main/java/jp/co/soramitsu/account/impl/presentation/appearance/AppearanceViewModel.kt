package jp.co.soramitsu.account.impl.presentation.appearance

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.theme.fw.FwAppearance
import jp.co.soramitsu.common.compose.theme.fw.FwAppearancePreferences
import jp.co.soramitsu.common.compose.theme.fw.FwTierChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppearanceState(
    val appearance: FwAppearance,
    /** The last choice could not be saved; the page says so and offers to retry. */
    val saveFailed: Boolean = false
)

/**
 * Display preferences only. They live in their own store, apart from wallet and authentication data,
 * and the theme picks every change up by itself. The screen offers Automatic and Reduced: on Android
 * Lite paints exactly like Full, so offering it would be a control that changes nothing.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val router: AccountRouter,
    context: Context
) : BaseViewModel() {

    private val preferences = FwAppearancePreferences(context)
    private val mutableState = MutableStateFlow(AppearanceState(displayed(preferences.read())))
    val state: StateFlow<AppearanceState> = mutableState.asStateFlow()

    fun back() = router.back()

    fun onReducedChanged(reduced: Boolean) {
        val choice = if (reduced) FwTierChoice.Reduced else FwTierChoice.Automatic
        save(state.value.appearance.copy(choice = choice))
    }

    fun onContrastChanged(enabled: Boolean) = save(state.value.appearance.copy(highContrast = enabled))

    fun retry() = save(state.value.appearance)

    private fun save(next: FwAppearance) {
        mutableState.value = AppearanceState(next)
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { preferences.save(next) }
            if (!saved && state.value.appearance == next) mutableState.value = AppearanceState(next, saveFailed = true)
        }
    }

    internal companion object {
        /** A stored Lite, which no version of this screen offers, reads as the Automatic it paints like. */
        fun displayed(stored: FwAppearance): FwAppearance =
            if (stored.choice == FwTierChoice.Lite) stored.copy(choice = FwTierChoice.Automatic) else stored
    }
}
