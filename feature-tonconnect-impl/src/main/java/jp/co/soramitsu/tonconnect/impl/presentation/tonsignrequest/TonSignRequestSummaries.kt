package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import jp.co.soramitsu.common.compose.component.SigningDetailViewState
import jp.co.soramitsu.common.compose.component.SigningSummarySection
import jp.co.soramitsu.common.compose.component.SigningSummaryViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatExactTokenAmount
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectMessageSummaries
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectMessageSummary
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectPayloadSummary
import jp.co.soramitsu.tonconnect.impl.domain.TonConnectPayloadSummary.ForwardPayload
import kotlinx.coroutines.withTimeoutOrNull
import org.ton.block.AddrStd
import java.math.BigInteger
import java.util.concurrent.CancellationException
import javax.inject.Inject

/** A jetton the user holds, as the network names it. */
data class TonJetton(val symbol: String, val decimals: Int)

/** What a TON Connect `sendTransaction` request does, for the approval screens. */
class TonSignRequestSummaries internal constructor(
    private val resourceManager: ResourceManager,
    private val jettonsOf: suspend (Chain, ByteArray) -> Map<String, TonJetton>
) {

    @Inject
    constructor(resourceManager: ResourceManager, tonRemoteSource: TonRemoteSource) : this(
        resourceManager,
        { chain, tonPublicKey -> userJettons(tonRemoteSource, chain, tonPublicKey) }
    )

    suspend fun summary(
        chain: Chain,
        request: TonConnectSignRequest,
        tonPublicKey: ByteArray
    ): SigningSummaryViewState {
        val messages = try {
            request.messages.map(TonConnectMessageSummaries::summary)
        } catch (error: CancellationException) {
            throw error
        } catch (ignored: Exception) {
            return undecoded()
        }
        val jettons = if (messages.any { it.payload is TonConnectPayloadSummary.JettonTransfer }) {
            jettonsOf(chain, tonPublicKey)
        } else {
            emptyMap()
        }
        return present(chain, messages, jettons)
    }

    // A request the wallet cannot read at all is shown as raw data only.
    private fun undecoded() = SigningSummaryViewState(
        headline = string(R.string.common_unknown),
        sections = emptyList(),
        warnings = listOf(string(R.string.signing_warning_undecoded)),
        showsRawData = true
    )

    internal fun present(
        chain: Chain,
        messages: List<TonConnectMessageSummary>,
        jettons: Map<String, TonJetton>
    ): SigningSummaryViewState {
        val sections = messages.mapIndexed { index, message ->
            SigningSummarySection(
                title = if (messages.size > 1) resourceManager.getString(R.string.signing_section_message, index + 1, messages.size) else null,
                rows = rows(chain, message, jettons)
            )
        }
        val total = messages.fold(BigInteger.ZERO) { sum, message -> sum + message.amount }
        val totalSection = SigningSummarySection(
            title = null,
            rows = listOf(SigningDetailViewState(string(R.string.common_total), tonAmount(chain, total)))
        ).takeIf { messages.size > 1 }
        val isComplete = messages.isNotEmpty() && messages.all { it.payload.isComplete && !it.deploysContract }
        val warnings = listOfNotNull(
            string(R.string.signing_warning_ton_state_init).takeIf { messages.any { it.deploysContract } },
            string(R.string.signing_warning_undecoded).takeIf { !isComplete }
        )
        return SigningSummaryViewState(
            headline = headline(chain, messages, jettons, total),
            sections = sections + listOfNotNull(totalSection),
            warnings = warnings,
            showsRawData = !isComplete
        )
    }

    private fun headline(
        chain: Chain,
        messages: List<TonConnectMessageSummary>,
        jettons: Map<String, TonJetton>,
        total: BigInteger
    ): String {
        val single = messages.singleOrNull()
        val amount = when (val payload = single?.payload) {
            null -> tonAmount(chain, total)
            is TonConnectPayloadSummary.JettonTransfer -> jettonAmount(single.destinationAddress, payload.amount, jettons)
            is TonConnectPayloadSummary.NftTransfer -> null
            else -> tonAmount(chain, single.amount)
        }
        val actions = messages.map { string(actionOf(it.payload)) }.distinct().joinToString(", ")
        return listOfNotNull(actions, amount).joinToString(" · ")
    }

    private fun rows(
        chain: Chain,
        message: TonConnectMessageSummary,
        jettons: Map<String, TonJetton>
    ): List<SigningDetailViewState> = buildList {
            add(row(R.string.signing_field_action, string(actionOf(message.payload))))
            when (val payload = message.payload) {
                TonConnectPayloadSummary.Empty,
                is TonConnectPayloadSummary.Comment -> {
                    add(row(R.string.signing_field_recipient, message.destination))
                    add(row(R.string.common_amount, tonAmount(chain, message.amount)))
                    (payload as? TonConnectPayloadSummary.Comment)?.text?.takeIf { it.isNotEmpty() }?.let {
                        add(row(R.string.signing_field_comment, it))
                    }
                }
                is TonConnectPayloadSummary.JettonTransfer -> {
                    add(row(R.string.signing_field_token, jettonOf(message.destinationAddress, jettons)?.symbol ?: unknownToken()))
                    add(row(R.string.signing_field_recipient, ownerAddress(payload.recipient)))
                    add(row(R.string.common_amount, jettonAmount(message.destinationAddress, payload.amount, jettons)))
                    add(row(R.string.signing_field_jetton_wallet, message.destination))
                    addForwarding(chain, payload.forwardTonAmount, payload.forwardPayload, payload.responseDestination)
                    add(row(R.string.signing_field_ton_attached, tonAmount(chain, message.amount)))
                }
                is TonConnectPayloadSummary.NftTransfer -> {
                    add(row(R.string.signing_field_nft, message.destination))
                    add(row(R.string.signing_field_recipient, ownerAddress(payload.newOwner)))
                    addForwarding(chain, payload.forwardTonAmount, payload.forwardPayload, payload.responseDestination)
                    add(row(R.string.signing_field_ton_attached, tonAmount(chain, message.amount)))
                }
                is TonConnectPayloadSummary.Undecoded -> {
                    add(row(R.string.signing_field_contract, message.destination))
                    add(row(R.string.common_amount, tonAmount(chain, message.amount)))
                }
            }
        }

    private fun MutableList<SigningDetailViewState>.addForwarding(
        chain: Chain,
        forwardTonAmount: BigInteger,
        forwardPayload: ForwardPayload,
        responseDestination: AddrStd?
    ) {
        if (forwardTonAmount.signum() > 0) add(row(R.string.signing_field_forward_amount, tonAmount(chain, forwardTonAmount)))
        if (forwardPayload is ForwardPayload.Comment && forwardPayload.text.isNotEmpty()) {
            add(row(R.string.signing_field_comment, forwardPayload.text))
        }
        responseDestination?.let { add(row(R.string.signing_field_excess_to, ownerAddress(it))) }
    }

    private fun actionOf(payload: TonConnectPayloadSummary): Int = when (payload) {
        TonConnectPayloadSummary.Empty,
        is TonConnectPayloadSummary.Comment -> R.string.signing_action_transfer
        is TonConnectPayloadSummary.JettonTransfer -> R.string.signing_action_jetton_transfer
        is TonConnectPayloadSummary.NftTransfer -> R.string.signing_action_nft_transfer
        is TonConnectPayloadSummary.Undecoded -> R.string.signing_action_contract_call
    }

    private fun jettonOf(jettonWallet: AddrStd, jettons: Map<String, TonJetton>): TonJetton? =
        jettons[jettonWallet.key()]

    private fun jettonAmount(
        jettonWallet: AddrStd,
        amount: BigInteger,
        jettons: Map<String, TonJetton>
    ): String = jettonOf(jettonWallet, jettons)?.let { amount.formatExactTokenAmount(it.decimals, it.symbol) }
            ?: resourceManager.getString(R.string.signing_value_raw_units, amount.toString())

    private fun tonAmount(chain: Chain, nanotons: BigInteger): String {
        val ton = chain.utilityAsset
        return nanotons.formatExactTokenAmount(ton?.precision ?: TON_DECIMALS, ton?.symbol ?: TON_SYMBOL)
    }

    private fun unknownToken() = string(R.string.signing_value_unknown_token)

    private fun row(titleId: Int, value: String) = SigningDetailViewState(string(titleId), value)

    private fun string(id: Int) = resourceManager.getString(id)

    companion object {
        private const val TON_DECIMALS = 9
        private const val TON_SYMBOL = "TON"
        private const val JETTONS_TIMEOUT_MILLIS = 10_000L

        /** Wallets show TON owner addresses in the non-bounceable form. */
        internal fun ownerAddress(address: AddrStd): String =
            address.toString(userFriendly = true, urlSafe = true, testOnly = false, bounceable = false)

        internal fun AddrStd.key(): String = toString(userFriendly = false).lowercase()

        // The user's jetton wallets: a jetton transfer is a message to one of them. The network's names
        // are used unless the chain lists the jetton itself. Without an answer amounts stay in raw units.
        private suspend fun userJettons(
            tonRemoteSource: TonRemoteSource,
            chain: Chain,
            tonPublicKey: ByteArray
        ): Map<String, TonJetton> = try {
            withTimeoutOrNull(JETTONS_TIMEOUT_MILLIS) {
                tonRemoteSource.loadJettonBalances(chain, tonPublicKey.tonAccountId(chain.isTestNet)).balances
            }.orEmpty().mapNotNull { balance ->
                val wallet = runCatching { AddrStd.parse(balance.walletAddress.address) }.getOrNull() ?: return@mapNotNull null
                val master = runCatching { AddrStd.parse(balance.jetton.address) }.getOrNull()
                val asset = chain.assets.firstOrNull { asset ->
                    master != null && runCatching { AddrStd.parse(asset.id).key() == master.key() }.getOrDefault(false)
                }
                wallet.key() to TonJetton(asset?.symbol ?: balance.jetton.symbol, asset?.precision ?: balance.jetton.decimals)
            }.toMap()
        } catch (error: CancellationException) {
            throw error
        } catch (ignored: Exception) {
            emptyMap()
        }
    }
}
