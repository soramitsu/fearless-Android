package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as SigningKeypair
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.xcm.ReviewedBridgeQuote
import jp.co.soramitsu.xcm.ReviewedBridgeTransferRequest
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeExecutor

/** Builds bridge requests only from the currently selected, locally signable Substrate account. */
class ReviewedPolkaswapBridgeInteractor(
    private val executor: ReviewedPolkaswapBridgeExecutor,
    private val chainRegistry: ChainRegistry,
    private val accountInteractor: AccountInteractor,
    private val signingKeypairProvider: KeypairProvider? = null
) {
    suspend fun quote(
        providerId: String,
        routeId: String,
        originChainId: String,
        destinationChainId: String,
        asset: Asset,
        recipientAddress: String,
        amount: BigDecimal
    ): ReviewedBridgeQuote = executor.quote(
        request(
            providerId,
            routeId,
            originChainId,
            destinationChainId,
            asset,
            recipientAddress,
            amount
        )
    )

    suspend fun submit(
        providerId: String,
        routeId: String,
        originChainId: String,
        destinationChainId: String,
        asset: Asset,
        recipientAddress: String,
        amount: BigDecimal,
        confirmedQuote: ReviewedBridgeQuote
    ): Result<String> = runCatching {
        executor.submit(
            request(
                providerId,
                routeId,
                originChainId,
                destinationChainId,
                asset,
                recipientAddress,
                amount
            ),
            confirmedQuote
        )
    }

    private suspend fun request(
        providerId: String,
        routeId: String,
        originChainId: String,
        destinationChainId: String,
        asset: Asset,
        recipientAddress: String,
        amount: BigDecimal
    ): ReviewedBridgeTransferRequest {
        val origin = chainRegistry.getChain(originChainId)
        val destination = chainRegistry.getChain(destinationChainId)
        val selected = accountInteractor.selectedMetaAccount()
        val accountId = requireNotNull(selected.accountId(origin)) {
            "cross_chain_selected_account_unavailable"
        }
        val cryptoType = requireNotNull(selected.substrateCryptoType) {
            "cross_chain_signable_account_required"
        }
        return ReviewedBridgeTransferRequest(
            providerId = providerId,
            routeId = routeId,
            originChain = origin,
            destinationChain = destination,
            asset = asset,
            senderAccountId = accountId,
            recipientAddress = recipientAddress,
            amountInPlanks = asset.planksFromAmount(amount),
            keypairProvider = signingKeypairProvider ?: object : KeypairProvider {
                override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray) = cryptoType
                override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): SigningKeypair =
                    error("An authorized signing provider is required")
            }
        )
    }
}
