package jp.co.soramitsu.staking.impl.domain.validators.current.search

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.address.createEthereumAddressIcon
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.validators.CollatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorSource
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private const val ELECTED_COLLATORS_CACHE = "ELECTED_COLLATORS_CACHE"
private const val ELECTED_VALIDATORS_CACHE = "ELECTED_VALIDATORS_CACHE"

class SearchCustomBlockProducerInteractor(
    private val collatorProvider: CollatorProvider,
    private val validatorProvider: ValidatorProvider,
    private val sharedState: StakingSharedState,
    private val computationalCache: ComputationalCache,
    private val addressIconGenerator: AddressIconGenerator,
    private val accountRepository: AccountRepository
) {

    private data class SelectionContext(
        val chain: Chain,
        val asset: Asset,
        val walletId: Long,
        val accountId: ByteArray?
    )

    private data class ContextKey(
        val chainId: String,
        val assetId: String,
        val stakingType: Asset.StakingType,
        val walletId: Long,
        val accountId: String?
    )

    /** Invalidates displayed catalogues when their selected chain, asset or account changes. */
    fun observeSelectionContext(): Flow<Unit> = combine(
        sharedState.assetWithChain, accountRepository.selectedMetaAccountFlow()
    ) { (chain, asset), wallet ->
        ContextKey(chain.id, asset.id, asset.staking, wallet.id, wallet.accountId(chain)?.toHexString())
    }.distinctUntilChanged().map { Unit }

    private suspend fun captureContext(): SelectionContext {
        val (chain, asset) = sharedState.assetWithChain.first()
        val wallet = accountRepository.getSelectedMetaAccount()
        return SelectionContext(chain, asset, wallet.id, wallet.accountId(chain)?.copyOf())
    }

    private suspend fun validateContext(context: SelectionContext) {
        val wallet = accountRepository.getSelectedMetaAccount()
        val (chain, asset) = sharedState.assetWithChain.first()
        currentCoroutineContext().ensureActive()
        val sameAsset = chain.id == context.chain.id && asset.id == context.asset.id && asset.staking == context.asset.staking
        val sameWallet = wallet.id == context.walletId &&
            wallet.accountId(context.chain)?.toHexString() == context.accountId?.toHexString()
        if (!sameAsset || !sameWallet) {
            throw CancellationException("Staking selection context changed")
        }
    }

    private fun ensureSetupUnchanged(process: SetupStakingSharedState, snapshot: SetupStakingProcess) {
        if (process.setupStakingProcess.value !== snapshot) throw CancellationException("Staking setup changed")
    }

    private suspend fun getCollators(context: SelectionContext, lifecycle: Lifecycle) = computationalCache.useCache(
            "$ELECTED_COLLATORS_CACHE:${context.chain.id}:${context.walletId}:${context.accountId?.toHexString()}",
            lifecycle
        ) {
            collatorProvider.getCollators(context.chain, context.accountId)
        }

    private suspend fun getValidators(chain: Chain, lifecycle: Lifecycle) =
        computationalCache.useCache("$ELECTED_VALIDATORS_CACHE:${chain.id}", lifecycle) {
            validatorProvider.getValidators(chain, ValidatorSource.Elected)
        }

    private suspend fun resolveValidator(
        chain: Chain,
        address: String,
        lifecycle: Lifecycle
    ): Validator {
        getValidators(chain, lifecycle).find { it.address == address }?.let { return it }
        if (chain.isValidAddress(address)) {
            val validator = validatorProvider.getValidatorWithoutElectedInfo(chain, address)
            if (validator.address == address && validator.prefs != null) return validator
        }
        error("cannot find validator")
    }

    suspend fun getBlockProducers(lifecycle: Lifecycle): List<BlockProducer> {
        val context = captureContext()
        val chain = context.chain
        val result = when (context.asset.staking) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                getValidators(chain, lifecycle).map {
                    BlockProducer(
                        it.identity?.display ?: it.address,
                        it.address,
                        (it.electedInfo?.apy ?: BigDecimal.ZERO).fractionToPercentage().formatAsPercentage()
                    )
                }
            }

            Asset.StakingType.PARACHAIN -> {
                getCollators(context, lifecycle).map { (_, collator) ->
                    BlockProducer(
                        collator.identity?.display ?: collator.address,
                        collator.address,
                        (collator.apy ?: BigDecimal.ZERO).formatAsPercentage()
                    )
                }
            }
        }
        validateContext(context)
        return result
    }

    suspend fun getBlockProducerByAddress(query: String): BlockProducer? {
        val context = captureContext()
        val chain = context.chain
        if (!chain.isValidAddress(query)) return null
        val result = when (context.asset.staking) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                val validator = validatorProvider.getValidatorWithoutElectedInfo(chain, query)
                validator.prefs?.let {
                    BlockProducer(
                        validator.identity?.display ?: validator.address,
                        validator.address,
                        (validator.electedInfo?.apy ?: BigDecimal.ZERO).fractionToPercentage().formatAsPercentage()
                    )
                }
            }
            Asset.StakingType.PARACHAIN -> {
                val collator = collatorProvider.getCollators(chain, context.accountId)[query.trim().fromHex().toHexString()]
                collator?.let {
                    BlockProducer(
                        it.identity?.display ?: it.address,
                        it.address,
                        (it.apy ?: BigDecimal.ZERO).formatAsPercentage()
                    )
                }
            }
        }
        validateContext(context)
        return result
    }

    suspend fun blockProducerSelected(
        address: String,
        setupStakingProcess: SetupStakingSharedState,
        lifecycle: Lifecycle
    ): Result<Unit> {
        val previousSetup = setupStakingProcess.setupStakingProcess.value
        val context = captureContext()
        val nextSetup = when (context.asset.staking) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                val selectedValidator = resolveValidator(context.chain, address, lifecycle)
                if (selectedValidator.prefs?.blocked == true) {
                    validateContext(context)
                    ensureSetupUnchanged(setupStakingProcess, previousSetup)
                    return Result.failure(BlockedValidatorException)
                }
                val ready = previousSetup as SetupStakingProcess.ReadyToSubmit.Stash
                val previous = ready.payload.blockProducers
                val selected = if (previous.any { it.accountIdHex == selectedValidator.accountIdHex }) {
                    previous.filterNot { it.accountIdHex == selectedValidator.accountIdHex }
                } else {
                    previous + selectedValidator
                }
                ready.changeBlockProducers(selected, SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM)
            }
            Asset.StakingType.PARACHAIN -> {
                val selected = listOf(getCollators(context, lifecycle)[address] ?: error("cannot find collator"))
                when (previousSetup) {
                    is SetupStakingProcess.ReadyToSubmit.Parachain -> previousSetup.changeBlockProducers(
                        selected, SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM
                    )
                    is SetupStakingProcess.SelectBlockProducersStep.Collators -> previousSetup.next(
                        selected, SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM
                    )
                    else -> error("Cannot set collators from $previousSetup state")
                }
            }
        }
        validateContext(context)
        if (!setupStakingProcess.setupStakingProcess.compareAndSet(previousSetup, nextSetup)) {
            throw CancellationException("Staking setup changed")
        }
        return Result.success(Unit)
    }

    suspend fun navigateBlockProducerInfo(
        address: String,
        lifecycle: Lifecycle,
        openCollatorInfo: (Collator) -> Unit,
        openValidatorInfo: (Validator) -> Unit
    ) {
        val context = captureContext()
        when (context.asset.staking) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                val validator = resolveValidator(context.chain, address, lifecycle)
                validateContext(context)
                openValidatorInfo(validator)
            }

            Asset.StakingType.PARACHAIN -> {
                val collator = getCollators(context, lifecycle)[address] ?: error("cannot find collator")
                validateContext(context)
                openCollatorInfo(collator)
            }
        }
    }

    suspend fun getIcon(
        address: String,
        sizeInDp: Int,
        type: Asset.StakingType
    ): PictureDrawable {
        return when (type) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                addressIconGenerator.createAddressIcon(address, sizeInDp)
            }

            Asset.StakingType.PARACHAIN -> {
                addressIconGenerator.createEthereumAddressIcon(address.requireHexPrefix(), sizeInDp)
            }
        }
    }

    data class BlockProducer(val name: String, val address: String, val rewardsPercent: String, val selected: Boolean = false)
}

object BlockedValidatorException : Exception("This validator is not accepting nominations at this moment. Please, try again in the next era.")
