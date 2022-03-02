package jp.co.soramitsu.feature_account_impl.presentation.exporting.seed

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
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

class ExportSeedViewModel(
    private val router: AccountRouter,
    resourceManager: ResourceManager,
    accountInteractor: AccountInteractor,
    chainRegistry: ChainRegistry,
    payload: ExportSeedPayload,
    private val clipboardManager: ClipboardManager,
) : ExportViewModel(
    accountInteractor,
    resourceManager,
    chainRegistry,
    payload.metaId,
    payload.chainId,
    payload.isExportWallet,
    ExportSource.Seed
) {

    val seedLiveData = isChainAccountLiveData.switchMap { isChainAccount ->
        when {
            isChainAccount -> chainSecretLiveData.map {
                ComponentHolder(
                    listOf(
                        if (isEthereum.value == false) it?.get(ChainAccountSecrets.Seed) else null,
                        if (isEthereum.value == true) it?.get(ChainAccountSecrets.Seed) else null
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

    fun back() {
        router.back()
    }

    fun exportClicked() {
        showSecurityWarning()
    }

    override fun securityWarningConfirmed() {
        val seed = seedLiveData.value?.component1<String>() ?: return
        val chainName = chainLiveData.value?.name ?: return

        exportText(resourceManager.getString(R.string.export_seed_without_derivation, chainName, seed))
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
