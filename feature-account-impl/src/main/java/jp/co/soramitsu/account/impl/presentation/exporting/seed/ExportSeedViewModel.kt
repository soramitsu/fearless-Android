package jp.co.soramitsu.account.impl.presentation.exporting.seed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.launch

@HiltViewModel
class ExportSeedViewModel @Inject constructor(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    chainRegistry: ChainRegistry,
    private val clipboardManager: ClipboardManager,
    private val savedStateHandle: SavedStateHandle
) : ExportViewModel(
    accountInteractor,
    resourceManager,
    chainRegistry,
    savedStateHandle.get<ExportSeedPayload>(ExportSeedFragment.PAYLOAD_KEY)!!.metaId,
    savedStateHandle.get<ExportSeedPayload>(ExportSeedFragment.PAYLOAD_KEY)!!.chainId,
    savedStateHandle.get<ExportSeedPayload>(ExportSeedFragment.PAYLOAD_KEY)!!.isExportWallet,
    ExportSource.Seed
) {

    val payload = savedStateHandle.get<ExportSeedPayload>(ExportSeedFragment.PAYLOAD_KEY)!!

    val exportSeedLiveData = isChainAccountLiveData.switchMap { isChainAccount ->
        when {
            isChainAccount -> chainSecretLiveData.map {
                ComponentHolder(
                    listOf(
                        if (isEthereum.value == false) it?.get(ChainAccountSecrets.Seed) else null,
                        if (isEthereum.value == true) it?.get(ChainAccountSecrets.Keypair)?.get(KeyPairSchema.PrivateKey) else null
                    ).map { seed -> seed?.toHexString(withPrefix = true) }
                )
            }
            else -> seedForSeedExportLiveData
        }
    }

    val seedDerivationPathLiveData = isChainAccountLiveData.switchMap { isChainAccount ->
        when {
            isChainAccount -> chainSecretLiveData.map {
                it?.get(ChainAccountSecrets.DerivationPath)
            }
            else -> liveData {
                accountInteractor.getSubstrateSecrets(metaId)?.get(SubstrateSecrets.SubstrateDerivationPath)
            }
        }
    }

    init {
        showSecurityWarning()
        markWalletBackedUp()
    }

    private fun markWalletBackedUp() {
        launch {
            accountInteractor.updateWalletBackedUp(payload.metaId)
        }
    }

    fun back() {
        router.back()
    }

    fun exportClicked() {
        showSecurityWarning()
    }

    override fun securityWarningCancel() {
        back()
    }

    fun substrateSeedClicked() {
        val seed = exportSeedLiveData.value?.component1<String>() ?: return
        copy(seed)
    }

    fun ethereumSeedClicked() {
        val seed = exportSeedLiveData.value?.component2<String>() ?: return
        copy(seed)
    }

    private fun copy(seed: String) {
        clipboardManager.addToClipboard(seed)
        showMessage(resourceManager.getString(R.string.common_copied))
    }
}
