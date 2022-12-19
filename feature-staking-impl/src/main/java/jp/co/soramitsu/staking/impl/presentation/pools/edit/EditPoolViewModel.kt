package jp.co.soramitsu.staking.impl.presentation.pools.edit

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditPoolViewModel @Inject constructor(
    private val router: StakingRouter,
    private val poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingInteractor: StakingInteractor
) : BaseViewModel(), EditPoolScreenInterface {

    companion object {
        private const val ROOT_WALLET_SELECTOR_TAG = "root"
        private const val NOMINATOR_WALLET_SELECTOR_TAG = "nominator"
        private const val STATE_TOGGLER_WALLET_SELECTOR_TAG = "state_toggler"
    }

    private val poolEditInfo = poolSharedStateProvider.requireEditPoolState
    private val chain = poolSharedStateProvider.requireMainState.requireChain

    private val poolNameFlow = MutableStateFlow(poolEditInfo.initialPoolName)
    private val selectedRootFlow = MutableStateFlow(poolEditInfo.initialRoot)
    private val selectedNominatorFlow = MutableStateFlow(poolEditInfo.initialNominator)
    private val selectedStateTogglerFlow = MutableStateFlow(poolEditInfo.initialStateToggler)

    val state = combine(poolNameFlow, selectedRootFlow, selectedNominatorFlow, selectedStateTogglerFlow) { poolName, root, nominator, stateToggler ->

        val prefix = chain.addressPrefix.toShort()
        val depositorAddress = poolEditInfo.depositor.toAddress(prefix)
        val rootAddress = root?.toAddress(prefix).orEmpty()
        val nominatorAddress = nominator?.toAddress(prefix).orEmpty()
        val stateTogglerAddress = stateToggler?.toAddress(prefix).orEmpty()

        val rootChanged = !root.contentEquals(poolEditInfo.initialRoot)
        val nominatorChanged = !nominator.contentEquals(poolEditInfo.initialNominator)
        val stateTogglerChanged = !stateToggler.contentEquals(poolEditInfo.initialStateToggler)

        val canContinue = poolName.isNotEmpty() && (poolName != poolEditInfo.initialPoolName || rootChanged || nominatorChanged || stateTogglerChanged)

        EditPoolViewState(
            poolName = poolName,
            root = rootAddress,
            depositor = depositorAddress,
            nominator = nominatorAddress,
            stateToggler = stateTogglerAddress,
            continueAvailable = canContinue
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        EditPoolViewState(
            poolName = poolEditInfo.initialPoolName,
            root = poolEditInfo.initialRoot?.toAddress(chain.addressPrefix.toShort()).orEmpty(),
            depositor = poolEditInfo.depositor.toAddress(chain.addressPrefix.toShort()),
            nominator = poolEditInfo.initialNominator?.toAddress(chain.addressPrefix.toShort()).orEmpty(),
            stateToggler = poolEditInfo.initialStateToggler?.toAddress(chain.addressPrefix.toShort()).orEmpty(),
            continueAvailable = false
        )
    )

    init {
        viewModelScope.launch {
            router.walletSelectorPayloadFlow.collect { payload ->
                payload?.let { onWalletSelected(it) }
            }
        }
    }

    private fun onWalletSelected(item: WalletSelectorPayload) {
        viewModelScope.launch {
            val (tag, selectedWalletId) = item
            val metaAccount = stakingInteractor.getMetaAccount(selectedWalletId)
            val accountId = metaAccount.accountId(chain) ?: return@launch
            when (tag) {
                NOMINATOR_WALLET_SELECTOR_TAG -> {
                    selectedNominatorFlow.value = accountId
                }
                STATE_TOGGLER_WALLET_SELECTOR_TAG -> {
                    selectedStateTogglerFlow.value = accountId
                }
                ROOT_WALLET_SELECTOR_TAG -> {
                    selectedRootFlow.value = accountId
                }
            }
        }
    }

    override fun onCloseClick() {
        router.back()
    }

    override fun onNameInput(text: String) {
        poolNameFlow.value = text
    }

    override fun onClearNameClick() {
        poolNameFlow.value = ""
    }

    override fun onRootClick() {
        router.openWalletSelector(ROOT_WALLET_SELECTOR_TAG)
    }

    override fun onNominatorClick() {
        router.openWalletSelector(NOMINATOR_WALLET_SELECTOR_TAG)
    }

    override fun onStateTogglerClick() {
        router.openWalletSelector(STATE_TOGGLER_WALLET_SELECTOR_TAG)
    }

    override fun onNextClick() {
        val newName = poolNameFlow.value.takeIf { it != poolEditInfo.initialPoolName }
        val newRoot = selectedRootFlow.value.takeIf { !it.contentEquals(poolEditInfo.initialRoot) }
        val newNominator = selectedNominatorFlow.value.takeIf { !it.contentEquals(poolEditInfo.initialNominator) }
        val newStateToggler = selectedStateTogglerFlow.value.takeIf { !it.contentEquals(poolEditInfo.initialStateToggler) }

        poolSharedStateProvider.editPoolState.set(
            poolSharedStateProvider.requireEditPoolState.copy(
                newPoolName = newName,
                newRoot = newRoot,
                newNominator = newNominator,
                newStateToggler = newStateToggler
            )
        )
        router.openEditPoolConfirm()
    }
}
