package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.WalletSendJournal
import jp.co.soramitsu.common.data.network.solana.SolanaBroadcastOptions
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.ext.universalWalletBitcoinIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only status checks and explicit same-byte retry for a payment retained before process loss. */
@Singleton
class NativeSendRecovery @Inject constructor(
    private val accounts: AccountRepository,
    private val registry: ChainRegistry,
    private val bitcoin: BitcoinIndexerClient,
    private val solana: SolanaRpcClient,
    preferences: Preferences,
    private val operations: OperationDao,
    private val pausePolicy: SendPausePolicy
) {
    private val journal = WalletSendJournal(preferences)

    /** Reads only local public metadata. The UI must retain this wallet/version-bound handle. */
    suspend fun peek(chainId: String, expectedWallet: Long): NativeSendRecoverySnapshot? {
        val epoch = WalletSelectionEpoch.snapshot()
        val chain = registry.getChain(chainId)
        if (!chain.isUniversalWalletBitcoin() && !chain.isUniversalWalletSolana()) return null
        val wallet = accounts.getSelectedMetaAccount()
        if (wallet.id != expectedWallet) fail()
        val sender = wallet.address(chain) ?: fail()
        val payment = journal.read(scope(wallet.id, chainId)) ?: return null
        requireKind(chain, payment)
        return NativeSendRecoverySnapshot(wallet.id, chainId, sender, epoch, payment.transactionId, payment.state, reviewed = payment.reviewed, feeUnits = payment.feeUnits,
            accountRentUnits = payment.accountRentUnits, kind = payment.kind, solanaNetwork = payment.solanaNetwork,
            currentSolanaGenesisHash = chain.universalWalletSolanaIndexerNetwork()?.genesisHash).also { requireCurrent(it, chain, false) }
    }

    /** Queries chain history first; retry may resubmit only the exact retained, still-valid bytes. */
    suspend fun refresh(expected: NativeSendRecoverySnapshot, retry: Boolean): NativeSendRecoverySnapshot {
        val chain = registry.getChain(expected.chainId)
        val scope = scope(expected.walletId, expected.chainId)
        return journal.withWalletLock(scope) {
            requireCurrent(expected, chain)
            var payment = journal.read(scope)?.takeIf { it.transactionId == expected.transactionId } ?: fail()
            requireKind(chain, payment)
            requirePayment(expected, payment)
            val rpc = if (payment.kind == PendingWalletSend.Kind.Solana) {
                solana.withGenesisHash(requireNotNull(payment.solanaNetwork).executionGenesisHash)
                    .withEndpoints(NativeSendRpcEndpoints.resolve(chain))
            } else null
            suspend fun settleObserved() {
                if (payment.state != PendingWalletSend.State.Pending) return
                requireCurrent(expected, chain)
                val observed = safely {
                    when (payment.kind) {
                        PendingWalletSend.Kind.Bitcoin -> bitcoin.transactionStatus(payment.transactionId,
                            requireNotNull(chain.universalWalletBitcoinIndexerNetwork()), bitcoinUrl(chain))
                            ?.takeIf { it.confirmed }?.let { PendingWalletSend.State.Confirmed }
                        PendingWalletSend.Kind.Solana -> requireNotNull(rpc).signatureStatus(payment.transactionId)?.takeIf { it.isConfirmed }
                            ?.let { if (it.errorJson == null) PendingWalletSend.State.Confirmed else PendingWalletSend.State.Failed }
                    }
                }
                requireCurrent(expected, chain)
                if (observed != null) payment = journal.settle(scope, payment, observed)
            }
            settleObserved()
            val expired = if (rpc != null && payment.state == PendingWalletSend.State.Pending) {
                safely { rpc.blockHeight() > requireNotNull(payment.lastValidBlockHeight) } ?: false
            } else false
            requireCurrent(expected, chain)
            if (retry && payment.state == PendingWalletSend.State.Pending) {
                WalletViewOnlyAccess.requireSigningAccess()
                if (pausePolicy.isPaused(chain)) throw pausePolicy.pausedError(chain)
                requireCurrent(expected, chain)
                currentCoroutineContext().ensureActive()
                if (!expired) safely {
                    when (payment.kind) {
                        PendingWalletSend.Kind.Bitcoin -> require(bitcoin.broadcastTransaction(payment.signedPayload,
                            requireNotNull(chain.universalWalletBitcoinIndexerNetwork()), bitcoinUrl(chain)).trim().lowercase() == payment.transactionId)
                        PendingWalletSend.Kind.Solana -> {
                            // Height/provider failure cannot authorize retry, nor a fresh blockhash/signature.
                            if (requireNotNull(rpc).blockHeight() <= requireNotNull(payment.lastValidBlockHeight)) {
                                requireCurrent(expected, chain)
                                WalletViewOnlyAccess.requireSigningAccess()
                                if (pausePolicy.isPaused(chain)) throw pausePolicy.pausedError(chain)
                                require(rpc.sendRawTransaction(payment.signedPayload, SolanaBroadcastOptions(maxRetries = 3)) == payment.transactionId)
                            }
                        }
                    }
                }
                settleObserved()
            }
            requireCurrent(expected, chain)
            if (payment.state != PendingWalletSend.State.Pending) {
                val status = if (payment.state == PendingWalletSend.State.Confirmed) OperationLocal.Status.COMPLETED else OperationLocal.Status.FAILED
                operations.getOperation(payment.transactionId)?.takeIf {
                    it.chainId == chain.id && it.address == expected.sender && it.source == OperationLocal.Source.APP
                }?.let { requireCurrent(expected, chain); operations.insert(it.copy(status = status)) }
            }
            expected.copy(state = payment.state, expired = expired)
        }
    }

    /** Validates local ownership again before a UI action opens an explorer for the retained network. */
    suspend fun explorerChain(expected: NativeSendRecoverySnapshot): Chain {
        val chain = registry.getChain(expected.chainId)
        val latest = requireCurrent(expected, chain)
        val payment = journal.read(scope(expected.walletId, expected.chainId)) ?: fail()
        requirePayment(expected, payment)
        requireKind(chain, payment)
        if (payment.solanaNetwork?.let { it.registryGenesisHash != it.executionGenesisHash } == true) fail()
        return latest
    }

    private suspend fun requireCurrent(expected: NativeSendRecoverySnapshot, chain: Chain, requireNetwork: Boolean = true): Chain {
        val current = accounts.getSelectedMetaAccount()
        val latest = if (expected.kind == PendingWalletSend.Kind.Solana) registry.getChain(expected.chainId) else chain
        if (!WalletSelectionEpoch.isCurrent(expected.selectionEpoch) || current.id != expected.walletId || current.address(latest) != expected.sender) fail()
        if (expected.kind == PendingWalletSend.Kind.Solana && requireNetwork &&
            (expected.solanaNetwork == null || latest.id != expected.chainId ||
                latest.universalWalletSolanaIndexerNetwork()?.genesisHash != expected.solanaNetwork.registryGenesisHash)) fail()
        return latest
    }

    private fun requirePayment(expected: NativeSendRecoverySnapshot, payment: PendingWalletSend) {
        if (payment.transactionId != expected.transactionId || payment.kind != expected.kind || payment.solanaNetwork != expected.solanaNetwork) fail()
    }

    private fun requireKind(chain: Chain, payment: PendingWalletSend) {
        val matches = when (payment.kind) {
            PendingWalletSend.Kind.Bitcoin -> chain.isUniversalWalletBitcoin()
            PendingWalletSend.Kind.Solana -> chain.isUniversalWalletSolana()
        }
        if (!matches) fail()
    }

    private fun bitcoinUrl(chain: Chain): String? = chain.externalApi?.history?.takeIf { it.type == Chain.ExternalApi.Section.Type.BITCOIN }?.url
    private fun scope(walletId: Long, chainId: String) = "$walletId:${chainId.length}:$chainId"
    private fun fail(): Nothing = throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
    private suspend fun <T> safely(block: suspend () -> T): T? = try { block() } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
}
