package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.feature_account_impl.presentation.view.mnemonic.mapMnemonicToMnemonicWords
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

class ExportMnemonicViewModel(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    chainRegistry: ChainRegistry,
    payload: ExportMnemonicPayload
) : ExportViewModel(
    accountInteractor,
    resourceManager,
    chainRegistry,
    payload.metaId,
    payload.chainId,
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

    val substrateDerivationPathLiveData = mediateWith(
        isChainAccountLiveData, isChainEthereumBased
    ) { (isChainAccount: Boolean?, isEthereum: Boolean?) ->
        when {
            isChainAccount == null || isEthereum == null -> null
            else -> isChainAccount to isEthereum
        }
    }
        .switchMap { (isChain, isEthereum) ->
            when {
                isChain && isEthereum -> MutableLiveData(null)
                isChain -> chainSecretLiveData.map {
                    it?.get(ChainAccountSecrets.DerivationPath)
                }
                else -> secretLiveData.map {
                    it?.get(MetaAccountSecrets.SubstrateDerivationPath)
                }
            }
        }

    val ethereumDerivationPathLiveData = mediateWith(
        isChainAccountLiveData, isChainEthereumBased
    ) { (isChainAccount: Boolean?, isEthereum: Boolean?) ->
        when {
            isChainAccount == null || isEthereum == null -> null
            else -> isChainAccount to isEthereum
        }
    }
        .switchMap { (isChain, isEthereum) ->
            when {
                isChain && !isEthereum -> MutableLiveData(null)
                isChain -> chainSecretLiveData.map {
                    it?.get(ChainAccountSecrets.DerivationPath)
                }
                else -> secretLiveData.map {
                    it?.get(MetaAccountSecrets.EthereumDerivationPath)
                }
            }
        }

    fun back() {
        router.back()
    }

    fun exportClicked() {
        showSecurityWarning()
    }

    override fun securityWarningConfirmed() {
        val mnemonic = mnemonicSourceLiveData.value ?: return

        val chainName = chainLiveData.value?.name ?: return

        val derivationPath = substrateDerivationPathLiveData.value

        val shareText = if (derivationPath.isNullOrBlank()) {
            resourceManager.getString(R.string.export_mnemonic_without_derivation, chainName, mnemonic)
        } else {
            resourceManager.getString(R.string.export_mnemonic_with_derivation, chainName, mnemonic, derivationPath)
        }

        exportText(shareText)
    }

    fun openConfirmMnemonic() {
        val mnemonicSource = mnemonicSourceLiveData.value ?: return

        router.openConfirmMnemonicOnExport(mnemonicSource.wordList)
    }
}
