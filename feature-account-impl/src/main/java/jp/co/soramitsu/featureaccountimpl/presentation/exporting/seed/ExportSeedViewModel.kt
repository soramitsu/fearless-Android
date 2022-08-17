package jp.co.soramitsu.featureaccountimpl.presentation.exporting.seed

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountInteractor
import jp.co.soramitsu.featureaccountapi.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.featureaccountimpl.presentation.AccountRouter
import jp.co.soramitsu.featureaccountimpl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import javax.inject.Inject

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

    val seedLiveData = isChainAccountLiveData.switchMap { isChainAccount ->
        when {
            isChainAccount -> chainSecretLiveData.map {
                ComponentHolder(
                    listOf(
                        if (isEthereum.value == false) it?.get(ChainAccountSecrets.Seed) else null,
                        if (isEthereum.value == true) it?.get(ChainAccountSecrets.Keypair)?.get(KeyPairSchema.PrivateKey) else null
                    ).map { seed -> seed?.toHexString(withPrefix = true) }
                )
            }
            else -> secretLiveData.map {
                ComponentHolder(
                    listOf(
                        it?.get(MetaAccountSecrets.Seed) ?: seedFromEntropy(it),
                        it?.get(MetaAccountSecrets.EthereumKeypair)?.get(KeyPairSchema.PrivateKey)
                    ).map { seed -> seed?.toHexString(withPrefix = true) }
                )
            }
        }
    }

    private fun seedFromEntropy(secret: EncodableStruct<MetaAccountSecrets>?) = secret?.get(MetaAccountSecrets.Entropy)?.let { entropy ->
        val mnemonicWords = MnemonicCreator.fromEntropy(entropy).words
        val derivationPath = secret[MetaAccountSecrets.SubstrateDerivationPath]?.nullIfEmpty()
        val password = derivationPath?.let { SubstrateJunctionDecoder.decode(it).password }
        SubstrateSeedFactory.deriveSeed32(mnemonicWords, password).seed
    }

    val derivationPathLiveData = isChainAccountLiveData.switchMap { isChainAccount ->
        when {
            isChainAccount -> chainSecretLiveData.map {
                it?.get(ChainAccountSecrets.DerivationPath)
            }
            else -> secretLiveData.map {
                it?.get(MetaAccountSecrets.SubstrateDerivationPath)
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

    override fun securityWarningCancel() {
        back()
    }

    fun substrateSeedClicked() {
        val seed = seedLiveData.value?.component1<String>() ?: return
        copy(seed)
    }

    fun ethereumSeedClicked() {
        val seed = seedLiveData.value?.component2<String>() ?: return
        copy(seed)
    }

    private fun copy(seed: String) {
        clipboardManager.addToClipboard(seed)
        showMessage(resourceManager.getString(R.string.common_copied))
    }
}
