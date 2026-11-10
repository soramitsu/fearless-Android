package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.account.api.domain.universalWalletRoot
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.runtime.ext.universalWalletBitcoinIndexerNetwork
import kotlinx.coroutines.CancellationException
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.model.Transfer

/** Binds each asynchronous planning/signing operation to the selected public wallet identity. */
class NativeSendAccountGuard(
    private val chain: Chain,
    private val accounts: AccountRepository,
    private val pausePolicy: SendPausePolicy,
    private val solanaNetwork: SolanaSendNetworkIdentity? = null,
    private val currentChain: suspend () -> Chain = { chain }
) {
    /** Exact registry identity and current sender are required; symbols do not authorize a token. */
    suspend fun bind(transfer: Transfer): NativeSendBinding {
        val epoch = jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot()
        val account = accounts.getSelectedMetaAccount()
        val asset = chain.assets.singleOrNull { it.id == transfer.chainAsset.id && it.currencyId == transfer.chainAsset.currencyId &&
            it.precision == transfer.chainAsset.precision && it.isNative == transfer.chainAsset.isNative }
            ?: throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT)
        if (account.address(chain) != transfer.sender) throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
        return NativeSendBinding(account.id, chain.id, asset.id, asset.currencyId, asset.precision, transfer.sender,
            transfer.recipient, transfer.amountInPlanks.toString(), transfer.sendMax, epoch, solanaNetwork).also { requireCurrent(it) }
    }

    /** Rechecks the veto and wallet after network/derivation suspensions, immediately before mutation. */
    suspend fun requireCurrent(binding: NativeSendBinding) {
        jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess.requireSigningAccess()
        if (pausePolicy.isPaused(chain)) throw pausePolicy.pausedError(chain)
        val current = accounts.getSelectedMetaAccount()
        val latest = if (solanaNetwork != null) currentChain() else chain
        if (solanaNetwork != null && (binding.solanaNetwork != solanaNetwork || latest.id != chain.id ||
            latest.universalWalletSolanaIndexerNetwork()?.genesisHash != solanaNetwork.registryGenesisHash ||
            latest.assets.none { it.id == binding.assetId && it.currencyId == binding.currencyId && it.precision == binding.precision })) {
            throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
        }
        if (!jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(binding.selectionEpoch) ||
            current.id != binding.metaId || current.address(latest) != binding.sender) {
            throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
        }
    }

    /**
     * Returns authorized root material transiently; the protocol signer independently proves its
     * address. A password wallet's account may derive with its BIP39 passphrase or, for an address
     * an earlier version showed, without it: the passphrase is the one that reproduces the sender.
     */
    suspend fun root(binding: NativeSendBinding): NativeSendRoot {
        requireCurrent(binding)
        accounts.getChainAccountSecrets(binding.metaId, chain.id)?.get(ChainAccountSecrets.Entropy)?.let {
            return NativeSendRoot(MnemonicCreator.fromEntropy(it.clone()).words, "")
        }
        val root = accounts.universalWalletRoot(binding.metaId)
            ?: throw NativeSendException(NativeSendException.Code.UNSUPPORTED_ACCOUNT)
        val candidates = root.passphraseCandidates
        val passphrase = candidates.singleOrNull()
            ?: candidates.firstOrNull { reproducesSender(root.mnemonic, it, binding.sender) }
            ?: candidates.first()
        return NativeSendRoot(root.mnemonic, passphrase)
    }

    private fun reproducesSender(mnemonic: String, passphrase: String, sender: String): Boolean = try {
        val bitcoin = chain.universalWalletBitcoinIndexerNetwork()
        if (bitcoin != null) {
            val network = if (bitcoin == BitcoinIndexerRoutes.Network.Mainnet) {
                BitcoinKeyDerivation.Network.Mainnet
            } else {
                BitcoinKeyDerivation.Network.Testnet
            }
            val account = BitcoinKeyDerivation.deriveAccount(mnemonic, passphrase, network)
            account.privateKey.fill(0)
            account.chainCode.fill(0)
            account.firstReceiveAddress == sender.lowercase()
        } else {
            val account = SolanaKeyDerivation.deriveAccount(mnemonic, passphrase)
            account.privateKey.fill(0)
            account.chainCode.fill(0)
            account.address == sender
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        false
    }
}

/** Transient signing root; never logged. */
class NativeSendRoot(val mnemonic: String, val passphrase: String) {
    override fun toString(): String = "NativeSendRoot(redacted)"
}
