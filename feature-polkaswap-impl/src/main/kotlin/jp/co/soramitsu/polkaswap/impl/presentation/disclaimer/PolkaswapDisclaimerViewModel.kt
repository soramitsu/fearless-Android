package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PolkaswapDisclaimerViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val polkaswapRouter: PolkaswapRouter
) : BaseViewModel(), DisclaimerScreenInterface {

    companion object {
        private const val delimiter = "%%"
    }

    private val highlightTextParams = listOf(
        TextWithHighlights.HighlightedTextParameters(colorAccent, "https://wiki.sora.org/polkaswap/polkaswap-faq"),
        TextWithHighlights.HighlightedTextParameters(colorAccent, "https://wiki.sora.org/ecosystem/what-is-polkaswap/terms"),
        TextWithHighlights.HighlightedTextParameters(colorAccent, "https://wiki.sora.org/ecosystem/what-is-polkaswap/privacy")
    )

    private val polkaswapMaintained = TextWithHighlights(
        resourceManager.getString(R.string.polkaswap_info_text_1),
        delimiter = delimiter,
        highlightTextParams
    )

    private val userResponsibilityTitle = resourceManager.getString(R.string.polkaswap_info_text_2)
    private val userResponsibilities = listOf(
        resourceManager.getString(R.string.polkaswap_info_text_3),
        resourceManager.getString(R.string.polkaswap_info_text_4),
        resourceManager.getString(R.string.polkaswap_info_text_5)
    )

    private val disclaimerReminder = TextWithHighlights(
        resourceManager.getString(R.string.polkaswap_info_text_6),
        delimiter = delimiter,
        highlightTextParams
    )

    private val hasRead = MutableStateFlow(polkaswapInteractor.hasReadDisclaimer)

    private val initialState = PolkaswapDisclaimerViewState(polkaswapMaintained, userResponsibilityTitle, userResponsibilities, disclaimerReminder, false)

    val state = hasRead.map {
        initialState.copy(hasReadChecked = it)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        initialState
    )

    override fun onLinkClick(url: String) {
        polkaswapRouter.openWebViewer(resourceManager.getString(R.string.tabbar_polkaswap_title), url)
    }

    override fun onContinueClick() {
        polkaswapInteractor.hasReadDisclaimer = true
        polkaswapRouter.back()
    }

    override fun onHasReadChecked() {
        hasRead.value = hasRead.value.not()
    }

    override fun onBackClick() {
        polkaswapRouter.back()
    }
}
