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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
class AppearanceViewModel internal constructor(
    private val router: AccountRouter,
    private val preferences: FwAppearancePreferences,
    private val persistenceDispatcher: CoroutineDispatcher
) : BaseViewModel() {

    @Inject constructor(router: AccountRouter, context: Context) : this(router, FwAppearancePreferences(context), Dispatchers.IO)

    private val saveMutex = Mutex()
    private var saveRevision = 0L
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
        val revision = ++saveRevision
        mutableState.value = AppearanceState(next)
        viewModelScope.launch {
            // Take the lock on Main, before dispatching IO, to persist rapid choices in tap order.
            saveMutex.withLock {
                val saved = withContext(persistenceDispatcher) { preferences.save(next) }
                // An older failed write must not replace a later choice or retry of the same value.
                if (revision == saveRevision) mutableState.value = AppearanceState(next, saveFailed = !saved)
            }
        }
    }

    internal companion object {
        /** A stored Lite, which no version of this screen offers, reads as the Automatic it paints like. */
        fun displayed(stored: FwAppearance): FwAppearance =
            if (stored.choice == FwTierChoice.Lite) stored.copy(choice = FwTierChoice.Automatic) else stored
    }
}
