package jp.co.soramitsu.account.impl.presentation.nomis_scoring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScoreDetailsViewModel @Inject constructor(
    private val router: AccountRouter,
    private val accountInteractor: AccountInteractor,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), ScoreDetailsScreenCallback {

    val state: MutableStateFlow<ScoreDetailsScreenState> = MutableStateFlow(
        ScoreDetailsScreenState(
            address = "",
            info = ScoreDetailsViewState.Loading
        )
    )

    private val rawAddress: MutableStateFlow<String> = MutableStateFlow("")

    init {
        val metaAccountId = requireNotNull(savedStateHandle.get<Long>(ScoreDetailsFragment.META_ACCOUNT_ID_KEY))
        nomisScoreInteractor.observeAccountScore(metaAccountId)
            .onEach { nomisData ->
                nomisData ?: return@onEach
                val newInfoState = when (nomisData.score) {
                    NomisScoreData.LOADING_CODE -> ScoreDetailsViewState.Loading
                    NomisScoreData.ERROR_CODE -> ScoreDetailsViewState.Error
                    else -> {
                        val scoredAt = nomisData.scoredAt?.let {
                            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            val date = Date(it)
                            formatter.format(date)
                        } ?: "0"

                        ScoreDetailsViewState.Success(
                            data = ScoreInfoState(
                                score = nomisData.score,
                                updated = scoredAt,
                                nativeBalanceUsd = nomisData.nativeBalanceUsd.formatFiat(null),
                                holdTokensUsd = nomisData.holdTokensUsd.formatFiat(null),
                                walletAge = resourceManager.getQuantityString(R.plurals.common_months_format, nomisData.walletAgeInMonths.toInt(), nomisData.walletAgeInMonths.toInt()),
                                totalTransactions = nomisData.totalTransactions.toString(),
                                rejectedTransactions = nomisData.rejectedTransactions.toString(),
                                avgTransactionTime = resourceManager.getQuantityString(R.plurals.common_hours_format, nomisData.avgTransactionTimeInHours.toInt(), nomisData.avgTransactionTimeInHours.toInt()),
                                maxTransactionTime = resourceManager.getQuantityString(R.plurals.common_hours_format, nomisData.maxTransactionTimeInHours.toInt(), nomisData.maxTransactionTimeInHours.toInt()),
                                minTransactionTime = resourceManager.getQuantityString(R.plurals.common_hours_format, nomisData.minTransactionTimeInHours.toInt(), nomisData.minTransactionTimeInHours.toInt()),
                            )
                        )
                    }
                }
                state.update { prevState -> prevState.copy(info = newInfoState) }
            }
            .catch {
                state.update { prevState -> prevState.copy(info = ScoreDetailsViewState.Error) }
                showError(it)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val metaAccount = accountInteractor.getMetaAccount(metaAccountId)
            val address = metaAccount.ethereumAddress?.toHexString(withPrefix = true)
            if (address != null) {
                rawAddress.value = address
                state.update { prevState -> prevState.copy(address = "${metaAccount.name} $address") }
            } else {
                state.update { prevState -> prevState.copy(info = ScoreDetailsViewState.Error) }
            }
        }
    }

    override fun onBackClicked() {
        router.back()
    }

    override fun onCloseClicked() {
        router.back()
    }

    override fun onCopyAddressClicked() {
        clipboardManager.addToClipboard(rawAddress.value)
        showMessage(resourceManager.getString(R.string.common_copied))
    }
}