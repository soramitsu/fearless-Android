package jp.co.soramitsu.feature_account_impl.presentation.exporting.seed

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.nullIfEmpty
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
    payload: ExportSeedPayload
) : ExportViewModel(
    accountInteractor,
    resourceManager,
    chainRegistry,
    payload.metaId,
    payload.chainId,
    ExportSource.Seed
) {

    val seedLiveData = secretLiveData.map {
        if (chainLiveData.value?.isEthereumBased == true) {
            it?.get(MetaAccountSecrets.EthereumKeypair)?.get(KeyPairSchema.PrivateKey)
        } else {
            it?.get(MetaAccountSecrets.Seed) ?: seedFromEntropy(it)
        }
    }
        .map { it?.toHexString(withPrefix = true) }

    private fun seedFromEntropy(secret: EncodableStruct<MetaAccountSecrets>?) = secret?.get(MetaAccountSecrets.Entropy)?.let { entropy ->
        val mnemonicWords = MnemonicCreator.fromEntropy(entropy).words
        val derivationPath = secret[MetaAccountSecrets.SubstrateDerivationPath]?.nullIfEmpty()
        val password = derivationPath?.let { SubstrateJunctionDecoder.decode(it).password }
        SubstrateSeedFactory.deriveSeed32(mnemonicWords, password).seed
    }

    val derivationPathLiveData = secretLiveData.map {
        it?.get(MetaAccountSecrets.SubstrateDerivationPath)
    }

    fun back() {
        router.back()
    }

    fun exportClicked() {
        showSecurityWarning()
    }

    override fun securityWarningConfirmed() {
        val seed = seedLiveData.value ?: return
        val chainName = chainLiveData.value?.name ?: return

        val derivationPath = derivationPathLiveData.value

        val shareText = if (derivationPath.isNullOrBlank()) {
            resourceManager.getString(R.string.export_seed_without_derivation, chainName, seed)
        } else {
            resourceManager.getString(R.string.export_seed_with_derivation, chainName, seed, derivationPath)
        }

        exportText(shareText)
    }
}
