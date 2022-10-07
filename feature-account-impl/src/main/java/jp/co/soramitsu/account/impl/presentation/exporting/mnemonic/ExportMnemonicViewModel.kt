package jp.co.soramitsu.account.impl.presentation.exporting.mnemonic

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicFragment.Companion.PAYLOAD_KEY
import jp.co.soramitsu.account.impl.presentation.view.mnemonic.mapMnemonicToMnemonicWords
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import javax.inject.Inject

@HiltViewModel
class ExportMnemonicViewModel @Inject constructor(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    chainRegistry: ChainRegistry,
    private val savedStateHandle: SavedStateHandle
) : ExportViewModel(
    accountInteractor,
    resourceManager,
    chainRegistry,
    savedStateHandle.get<ExportMnemonicPayload>(PAYLOAD_KEY)!!.metaId,
    savedStateHandle.get<ExportMnemonicPayload>(PAYLOAD_KEY)!!.chainId,
    savedStateHandle.get<ExportMnemonicPayload>(PAYLOAD_KEY)!!.isExportWallet,
    ExportSource.Mnemonic
) {

    private val isChainEthereumBased = chainLiveData.map { it.isEthereumBased }

    private val mnemonicSourceLiveData: LiveData<Mnemonic> = isChainAccountLiveData.switchMap { isChainAccount ->
        when {
            isChainAccount -> chainSecretLiveData.map {
                it?.get(ChainAccountSecrets.Entropy)?.let { MnemonicCreator.fromEntropy(it) } ?: throw IllegalArgumentException("Mnemonic not specified")
            }
            else -> secretLiveData.map {
                it?.get(MetaAccountSecrets.Entropy)?.let { MnemonicCreator.fromEntropy(it) } ?: throw IllegalArgumentException("Mnemonic not specified")
            }
        }
    }

    val mnemonicWordsLiveData = mnemonicSourceLiveData.map {
        mapMnemonicToMnemonicWords(it.wordList)
    }

    val derivationPathLiveData = mediateWith(isChainAccountLiveData, isChainEthereumBased) { (isChainAccount: Boolean?, isEthereum: Boolean?) ->
        when {
            isChainAccount == null || isEthereum == null -> null
            else -> isChainAccount to isEthereum
        }
    }
        .switchMap { (isChain, isEthereum) ->
            when {
                isChain && !isEthereum -> chainSecretLiveData.map {
                    ComponentHolder(
                        listOf(
                            it?.get(ChainAccountSecrets.DerivationPath),
                            null
                        )
                    )
                }
                isChain -> chainSecretLiveData.map {
                    ComponentHolder(
                        listOf(
                            null,
                            it?.get(ChainAccountSecrets.DerivationPath)
                        )
                    )
                }
                else -> secretLiveData.map {
                    ComponentHolder(
                        listOf(
                            it?.get(MetaAccountSecrets.SubstrateDerivationPath),
                            it?.get(MetaAccountSecrets.EthereumDerivationPath).takeIf { path ->
                                path != BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
                            }
                        )
                    )
                }
            }
        }

    init {
        showSecurityWarning()
    }

    fun back() {
        router.back()
    }

    fun exportClicked() {
        showSecurityWarning()
    }

    fun openConfirmMnemonic() {
        val mnemonicSource = mnemonicSourceLiveData.value ?: return

        router.openConfirmMnemonicOnExport(mnemonicSource.wordList)
    }

    override fun securityWarningCancel() {
        back()
    }
}
