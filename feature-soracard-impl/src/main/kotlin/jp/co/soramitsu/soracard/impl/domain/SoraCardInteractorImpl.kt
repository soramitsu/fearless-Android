package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.compose.component.SoraCardProgress
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.splitVersions
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.soracard.api.domain.SoraCardAvailabilityInfo
import jp.co.soramitsu.soracard.api.domain.SoraCardBasicStatus
import jp.co.soramitsu.soracard.api.domain.SoraCardInteractor
import jp.co.soramitsu.soracard.api.util.SoraCardOptions
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.min

class SoraCardInteractorImpl @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val soraCardClientProxy: SoraCardClientProxy,
    private val preferences: Preferences,
) : SoraCardInteractor {

    companion object {
        private const val PREFS_SORA_CARD_BUY_XOR_VISIBILITY = "prefs_sora_card_buy_xor_visibility"
        private const val PREFS_SORA_CARD_PROGRESS = "prefs_sora_card_progress"
        private const val POLLING_PERIOD_IN_MILLIS = 90_000L
    }

    private val _soraCardBasicStatus = MutableStateFlow(
        SoraCardBasicStatus(
            initialized = false,
            initError = null,
            availabilityInfo = null,
            verification = SoraCardCommonVerification.NotFound,
            needInstallUpdate = false,
            applicationFee = null,
            ibanInfo = null,
            phone = null,
        )
    )

    private val _ibanFlow = MutableStateFlow<IbanInfo?>(null)
    private val _phoneFlow = MutableStateFlow("")
    private val _verStatus = MutableStateFlow(SoraCardCommonVerification.NotFound)

    override fun isShowBuyXor(): Boolean =
        preferences.getBoolean(PREFS_SORA_CARD_BUY_XOR_VISIBILITY, true)

    override fun hideBuyXor() {
        preferences.putBoolean(PREFS_SORA_CARD_BUY_XOR_VISIBILITY, false)
    }

    override fun getSoraCardProgress(): SoraCardProgress =
        SoraCardProgress.entries[preferences.getInt(PREFS_SORA_CARD_PROGRESS, 0)]

    private fun setSoraCardProgress(p: SoraCardProgress) {
        val cur = preferences.getInt(PREFS_SORA_CARD_PROGRESS, 0)
        if (p.ordinal > cur) {
            preferences.putInt(PREFS_SORA_CARD_PROGRESS, p.ordinal)
        }
    }

    override val soraCardChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId

    override fun xorAssetFlow(): Flow<Asset> = flow {
        val chain = chainRegistry.getChain(soraCardChainId)
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val substrateAccountId = metaAccount.substrateAccountId
        val xorAsset = chain.assets.firstOrNull { it.isUtility }
        if (xorAsset == null || substrateAccountId == null) {
            emitAll(emptyFlow())
        } else {
            emitAll(
                walletRepository.assetFlow(
                    metaId = metaAccount.id,
                    accountId = substrateAccountId,
                    chainAsset = xorAsset,
                    minSupportedVersion = chain.minSupportedVersion,
                )
            )
        }
    }

    override val basicStatus: StateFlow<SoraCardBasicStatus> = _soraCardBasicStatus.asStateFlow()

    @OptIn(FlowPreview::class)
    @Suppress("UNCHECKED_CAST")
    override suspend fun initialize() {
        coroutineScope {
            launch {
                resetInfo()
            }
            launch {
                checkSoraCardPending()
            }
            combine(
                flow { emit(soraCardClientProxy.init()) },
                needInstallUpdate(),
                fetchApplicationFee(),
                _ibanFlow.asStateFlow(),
                subscribeToSoraCardAvailabilityFlow(),
                _verStatus.asStateFlow(),
                _phoneFlow.asStateFlow(),
            ) { flows ->
                val init = flows[0] as Pair<Boolean, String>
                val needUpdate = flows[1] as Boolean
                val fee = flows[2] as String
                val ibanInfo = flows[3] as IbanInfo?
                val availability = flows[4] as SoraCardAvailabilityInfo
                val verification = flows[5] as SoraCardCommonVerification
                val phone = flows[6] as String
                SoraCardBasicStatus(
                    initialized = init.first,
                    initError = init.second,
                    availabilityInfo = availability,
                    verification = verification,
                    needInstallUpdate = needUpdate,
                    applicationFee = fee,
                    ibanInfo = ibanInfo,
                    phone = phone,
                )
            }
                .debounce(1000)
                .collectLatest {
                    val next =
                        if (it.ibanInfo == null && isKycStatus(it.verification).not()) {
                            SoraCardProgress.START
                        } else {
                            SoraCardProgress.KYC_IBAN
                        }
                    setSoraCardProgress(next)
                    _soraCardBasicStatus.value = it
                }
        }
    }

    private fun isKycStatus(status: SoraCardCommonVerification): Boolean {
        return status == SoraCardCommonVerification.Failed ||
                status == SoraCardCommonVerification.Rejected ||
                status == SoraCardCommonVerification.Pending ||
                status == SoraCardCommonVerification.Successful
    }

    override suspend fun setLogout() {
        preferences.putInt(PREFS_SORA_CARD_PROGRESS, SoraCardProgress.START.ordinal)
        soraCardClientProxy.logout()
        _verStatus.value = SoraCardCommonVerification.NotFound
        _ibanFlow.value = null
        _phoneFlow.value = ""
    }

    override suspend fun setStatus(status: SoraCardCommonVerification) {
        _verStatus.value = status
        resetInfo()
    }

    //region private

    private suspend fun checkSoraCardPending() {
        var isLoopInProgress = true
        while (isLoopInProgress) {
            val status =
                soraCardClientProxy.getKycStatus().getOrDefault(SoraCardCommonVerification.NotFound)
            _verStatus.value = status
            if (status != SoraCardCommonVerification.Pending) {
                isLoopInProgress = false
            } else {
                delay(POLLING_PERIOD_IN_MILLIS)
            }
        }
    }

    private fun needInstallUpdate() = flow {
        emit(needInstallUpdateInternal())
    }

    private suspend fun needInstallUpdateInternal(): Boolean {
        val remote = soraCardClientProxy.getVersion().getOrNull() ?: return false
        val currentArray = SoraCardOptions.soracard.splitVersions()
        val remoteArray = remote.splitVersions()
        if (currentArray.isEmpty() || remoteArray.isEmpty()) return false
        for (i in 0..min(currentArray.lastIndex, remoteArray.lastIndex)) {
            if (remoteArray[i] > currentArray[i]) return true
        }
        return false
    }

    private suspend fun resetInfo() {
        fetchUserIbanAccount()
        fetchUserPhone()
    }

    private fun subscribeToSoraCardAvailabilityFlow() =
        xorAssetFlow()
            .distinctUntilChanged { old, new ->
                old.transferable == new.transferable &&
                        old.token.configuration.priceId == new.token.configuration.priceId &&
                        old.token.configuration.precision == new.token.configuration.precision
            }
            .map { asset ->
                val totalTokenBalance = asset.transferable
                SoraCardAvailabilityInfo(
                    xorBalance = totalTokenBalance,
                    xorRatioAvailable = true,
                )
            }.flowOn(Dispatchers.IO)

    private fun errorInfoState(balance: BigDecimal = BigDecimal.ZERO) = SoraCardAvailabilityInfo(
        xorBalance = balance,
        xorRatioAvailable = false,
    )

    private suspend fun fetchUserIbanAccount() {
        _ibanFlow.value = soraCardClientProxy.getIBAN().getOrNull()
    }

    private suspend fun fetchUserPhone() {
        _phoneFlow.value = soraCardClientProxy.getPhone()
    }

    private fun fetchApplicationFee() = flow { emit(soraCardClientProxy.getApplicationFee()) }
    //endregion
}
