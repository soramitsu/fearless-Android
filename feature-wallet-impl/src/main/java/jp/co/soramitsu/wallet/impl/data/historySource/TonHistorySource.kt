package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.runtime.binding.bindActiveEraIndex
import jp.co.soramitsu.common.data.network.ton.AccountEventAction
import jp.co.soramitsu.common.utils.toUserFriendly
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import org.ton.api.pub.PublicKeyEd25519
import kotlin.math.abs
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TonHistorySource(
    private val tonRemoteSource: TonRemoteSource,
    private val historyUrl: String
) : HistorySource {

    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        val tonPublicKey = PublicKeyEd25519(accountId)
        val contract = V4R2WalletContract(tonPublicKey)

        val accountEvents =
            tonRemoteSource.getAccountEvents(historyUrl, contract.getAccountId(chain.isTestNet))

        val filteredActions = if (chainAsset.type == ChainAssetType.Normal) {
            accountEvents.events.filter { event -> event.actions.all { !it.isJetton() } }
        } else {
            accountEvents.events.filter { event -> event.actions.all { it.isJetton() } }
        }
        val operations = filteredActions.map { event ->
            val fee = if (0 > event.extra) {
                abs(event.extra)
            } else {
                0
            }.toBigInteger()
            event.actions.map { action ->
                val status = if (action.status == AccountEventAction.Status.failed) {
                    Operation.Status.FAILED
                } else if (action.status == AccountEventAction.Status.ok) {
                    Operation.Status.COMPLETED
                } else if (event.inProgress) {
                    Operation.Status.PENDING
                } else throw IllegalStateException("Unknown ton transfer status")
                val operation = when {
                    action.tonTransfer != null -> {
                        val tonTransfer = action.tonTransfer!!

                        Operation.Type.Transfer(
                            hash = event.eventId,
                            myAddress = accountAddress,
                            amount = tonTransfer.amount.toBigInteger(),
                            receiver = tonTransfer.recipient.address.toUserFriendly(
                                wallet = tonTransfer.recipient.isWallet,
                                testnet = chain.isTestNet
                            ),
                            sender = tonTransfer.sender.address.toUserFriendly(
                                wallet = tonTransfer.sender.isWallet,
                                testnet = chain.isTestNet
                            ),
                            status = status,
                            fee = fee
                        )
                    }

                    action.jettonTransfer != null && action.jettonTransfer!!.jetton.address == chainAsset.id -> {
                        val jettonTransfer = action.jettonTransfer!!
                        Operation.Type.Transfer(
                            hash = event.eventId,
                            myAddress = accountAddress,
                            amount = jettonTransfer.amount.toBigInteger(),
                            receiver = jettonTransfer.recipient?.address?.toUserFriendly(
                                wallet = jettonTransfer.recipient?.isWallet ?: true,
                                testnet = chain.isTestNet
                            ).orEmpty(),
                            sender = jettonTransfer.sender?.address?.toUserFriendly(
                                wallet = jettonTransfer.sender?.isWallet ?: true,
                                testnet = chain.isTestNet
                            ).orEmpty(),
                            status = status,
                            fee = fee
                        )
                    }

                    else -> {
                        Operation.Type.Extrinsic(
                            hash = event.eventId,
                            module = "",
                            call = action.type.value,
                            fee = fee,
                            status = status
                        )
                    }
                }

                Operation(
                    id = event.eventId,
                    address = accountAddress,
                    time = event.timestamp.toDuration(DurationUnit.SECONDS).inWholeMilliseconds,
                    chainAsset = chainAsset,
                    type = operation
                )
            }
        }.flatten()

        return CursorPage(null, operations)
    }
}