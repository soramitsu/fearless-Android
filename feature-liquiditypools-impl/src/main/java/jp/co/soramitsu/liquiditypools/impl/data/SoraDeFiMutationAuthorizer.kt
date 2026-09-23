package jp.co.soramitsu.liquiditypools.impl.data

import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount

internal enum class SoraMutationFeature {
    Liquidity,
    Demeter
}

internal data class SoraRuntimeCall(
    val pallet: String,
    val call: String
)

internal data class CanonicalSoraAsset(
    val id: String,
    val chainId: ChainId,
    val currencyId: String?,
    val precision: Int
) {
    constructor(asset: Asset) : this(
        id = asset.id,
        chainId = asset.chainId,
        currencyId = asset.currencyId,
        precision = asset.precision
    )
}

internal data class SoraMutationIdentity(
    val metaId: Long,
    val accountIdHex: String
)

internal data class SoraMutationContext(
    val chain: Chain,
    val accountId: ByteArray,
    val accountAddress: String,
    val identity: SoraMutationIdentity,
    val assets: List<Asset>,
    val feeAsset: Asset
)

internal data class SoraAssetSpend(
    val asset: Asset,
    val amountInPlanks: BigInteger,
    val insufficientReason: String
)

/**
 * Resolves every mutable authority input from the current SORA context. Repositories invoke this
 * once before fee estimation and again directly before submitting so stale UI/domain objects do
 * not authorize a transaction.
 */
open class SoraDeFiMutationAuthorizer @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val featureToggleStore: ProductFeatureToggleStore
) {

    internal open suspend fun capabilityReason(
        feature: SoraMutationFeature,
        chainId: ChainId,
        requiredCalls: Set<SoraRuntimeCall>
    ): String? {
        val failure = runCatching {
            authorize(
                feature = feature,
                chainId = chainId,
                requestedAssets = emptyList(),
                requiredCalls = requiredCalls
            )
        }.exceptionOrNull() ?: return null

        return failure.message ?: "SORA mutation context is unavailable"
    }

    internal open suspend fun authorize(
        feature: SoraMutationFeature,
        chainId: ChainId,
        requestedAssets: List<CanonicalSoraAsset>,
        requiredCalls: Set<SoraRuntimeCall>,
        expectedIdentity: SoraMutationIdentity? = null
    ): SoraMutationContext {
        checkFeatureSwitch(feature)
        check(chainId == soraMainChainId) { "This action requires the exact SORA main network" }

        val chain = chainRegistry.getChain(chainId)
        check(chain.id == chainId) { "Resolved SORA chain identity does not match the request" }
        val runtime = chainRegistry.getRuntimeOrNull(chainId)
            ?: throw IllegalStateException("SORA runtime is unavailable")
        requiredCalls.forEach { required ->
            check(runtime.metadata.moduleOrNull(required.pallet)?.calls?.get(required.call) != null) {
                "SORA runtime call ${required.pallet}.${required.call} is unavailable"
            }
        }

        val metaAccount = accountRepository.getSelectedMetaAccount()
        val accountId = metaAccount.accountId(chain)
            ?: throw IllegalStateException("Add a SORA account before continuing")
        check(!accountRepository.isWalletRecoveryRequired(metaAccount.id)) {
            "Recover this wallet's signing material before continuing"
        }
        val hasSigningMaterial = if (metaAccount.hasChainAccount(chain.id)) {
            accountRepository.getChainAccountSecrets(metaAccount.id, chain.id) != null
        } else {
            accountRepository.getSubstrateSecrets(metaAccount.id) != null
        }
        check(hasSigningMaterial) { "This SORA account is watch-only or has no supported signer" }

        val identity = SoraMutationIdentity(metaAccount.id, accountId.toHexString(withPrefix = true))
        check(expectedIdentity == null || identity == expectedIdentity) {
            "The selected SORA account changed while authorizing this action"
        }

        val canonicalAssets = requestedAssets.map { requested ->
            check(requested.chainId == chainId) { "Requested asset belongs to a different network" }
            val currencyId = requested.currencyId?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Requested asset has no exact SORA currency id")
            val canonical = chain.assets.singleOrNull { it.currencyId == currencyId }
                ?: throw IllegalStateException("SORA currency id is not uniquely registered")
            check(
                canonical.id == requested.id &&
                    canonical.chainId == chainId &&
                    canonical.precision == requested.precision
            ) { "SORA asset identity or precision changed" }
            check(canonical.precision >= 0) { "SORA asset precision is invalid" }
            canonical
        }
        val feeAsset = chain.assets.singleOrNull {
            it.chainId == chainId && it.isUtility &&
                it.currencyId.equals(XOR_CURRENCY_ID, ignoreCase = true) &&
                it.precision == XOR_PRECISION
        } ?: throw IllegalStateException("The exact XOR fee asset is unavailable on SORA")

        return SoraMutationContext(
            chain = chain,
            accountId = accountId,
            accountAddress = chain.addressOf(accountId),
            identity = identity,
            assets = canonicalAssets,
            feeAsset = feeAsset
        )
    }

    internal open suspend fun requireFreshBalances(
        context: SoraMutationContext,
        spends: List<SoraAssetSpend>,
        feeInPlanks: BigInteger
    ) {
        check(feeInPlanks >= BigInteger.ZERO) { "XOR fee estimate must not be negative" }
        spends.forEach {
            check(it.amountInPlanks > BigInteger.ZERO) { "Input amount must be at least one plank" }
        }

        val requiredByCurrencyId = linkedMapOf<String, Pair<Asset, BigInteger>>()
        spends.forEach { spend ->
            val currencyId = requireNotNull(spend.asset.currencyId)
            val existing = requiredByCurrencyId[currencyId]?.second ?: BigInteger.ZERO
            requiredByCurrencyId[currencyId] = spend.asset to (existing + spend.amountInPlanks)
        }
        val feeCurrencyId = requireNotNull(context.feeAsset.currencyId)
        val existingFeeAssetSpend = requiredByCurrencyId[feeCurrencyId]?.second ?: BigInteger.ZERO
        requiredByCurrencyId[feeCurrencyId] = context.feeAsset to (existingFeeAssetSpend + feeInPlanks)

        requiredByCurrencyId.forEach { (currencyId, required) ->
            val freshBalance = walletRepository.getAccountSpendableBalance(required.first, context.accountId)
            check(freshBalance >= required.second) {
                spends.firstOrNull { it.asset.currencyId == currencyId }?.insufficientReason
                    ?: "XOR balance is insufficient for the network fee"
            }
        }
    }

    internal open fun checkFeatureSwitch(feature: SoraMutationFeature) {
        when (feature) {
            SoraMutationFeature.Liquidity -> check(featureToggleStore.polkaswapMutationsEnabled) {
                "Polkaswap liquidity actions are temporarily disabled"
            }

            SoraMutationFeature.Demeter -> check(featureToggleStore.demeterMutationsEnabled) {
                "Demeter actions are temporarily unavailable"
            }
        }
    }

    private companion object {
        const val XOR_CURRENCY_ID =
            "0x0200000000000000000000000000000000000000000000000000000000000000"
        const val XOR_PRECISION = 18
    }
}

internal fun Asset.exactPositivePlanks(amount: BigDecimal, label: String): BigInteger {
    check(amount > BigDecimal.ZERO) { "$label must be greater than zero" }
    val decimalPlaces = amount.stripTrailingZeros().scale().coerceAtLeast(0)
    check(decimalPlaces <= precision) { "$label exceeds the canonical asset precision" }
    return planksFromAmount(amount).also {
        check(it > BigInteger.ZERO) { "$label must be at least one plank" }
    }
}
