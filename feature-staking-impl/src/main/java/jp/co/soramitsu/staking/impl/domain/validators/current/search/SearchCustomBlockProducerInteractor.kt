package jp.co.soramitsu.staking.impl.domain.validators.current.search

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.Lifecycle
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.address.createEthereumAddressIcon
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.toggle
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.validators.CollatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorSource
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.validators.change.setCustomCollators
import jp.co.soramitsu.staking.impl.presentation.validators.change.setCustomValidators
import kotlinx.coroutines.flow.first

private const val ELECTED_COLLATORS_CACHE = "ELECTED_COLLATORS_CACHE"
private const val ELECTED_VALIDATORS_CACHE = "ELECTED_VALIDATORS_CACHE"

class SearchCustomBlockProducerInteractor(
    private val collatorProvider: CollatorProvider,
    private val validatorProvider: ValidatorProvider,
    private val sharedState: StakingSharedState,
    private val computationalCache: ComputationalCache,
    private val addressIconGenerator: AddressIconGenerator
) {

    private suspend fun getCollators(lifecycle: Lifecycle) = computationalCache.useCache(ELECTED_COLLATORS_CACHE, lifecycle) {
        collatorProvider.getCollators(sharedState.chain())
    }

    private suspend fun getValidators(lifecycle: Lifecycle) = computationalCache.useCache(ELECTED_VALIDATORS_CACHE, lifecycle) {
        validatorProvider.getValidators(sharedState.chain(), ValidatorSource.Elected)
    }

    suspend fun getBlockProducers(lifecycle: Lifecycle): List<BlockProducer> {
        val asset = sharedState.assetWithChain.first().asset
        return when (asset.staking) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                getValidators(lifecycle).map {
                    BlockProducer(
                        it.identity?.display ?: it.address,
                        it.address,
                        (it.electedInfo?.apy ?: BigDecimal.ZERO).fractionToPercentage().formatAsPercentage()
                    )
                }
            }

            Asset.StakingType.PARACHAIN -> {
                getCollators(lifecycle).map { (_, collator) ->
                    BlockProducer(
                        collator.identity?.display ?: collator.address,
                        collator.address,
                        (collator.apy ?: BigDecimal.ZERO).formatAsPercentage()
                    )
                }
            }
        }
    }

    suspend fun getBlockProducerByAddress(query: String): BlockProducer? {
        val (chain, asset) = sharedState.assetWithChain.first()
        return if (chain.isValidAddress(query)) {
            when (asset.staking) {
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
                    val collator = collatorProvider.getCollators(chain)[query.requireHexPrefix()]
                    collator?.let {
                        BlockProducer(
                            it.identity?.display ?: it.address,
                            it.address,
                            (it.apy ?: BigDecimal.ZERO).formatAsPercentage()
                        )
                    }
                }
            }
        } else {
            null
        }
    }

    suspend fun blockProducerSelected(address: String, setupStakingProcess: SetupStakingSharedState, lifecycle: Lifecycle): Result<Unit> {
        val asset = sharedState.assetWithChain.first().asset
        when (asset.staking) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                val validators = getValidators(lifecycle)
                val selectedValidator = validators.find { it.address == address } ?: error("cannot find validator")
                if (selectedValidator.prefs?.blocked == true) return Result.failure(BlockedValidatorException)
                val selectedValidators =
                    setupStakingProcess.get<SetupStakingProcess.ReadyToSubmit.Stash>().payload.blockProducers.toSet().toggle(selectedValidator)
                try {
                    setupStakingProcess.setCustomValidators(selectedValidators.toList())
                } catch (e: Exception) {
                    return Result.failure(e)
                }
                return Result.success(Unit)
            }

            Asset.StakingType.PARACHAIN -> {
                val collators = getCollators(lifecycle)
                val selectedCollators = collators[address]?.let { listOf(it) } ?: emptyList()
                setupStakingProcess.setCustomCollators(selectedCollators)
                return Result.success(Unit)
            }
        }
    }

    suspend fun navigateBlockProducerInfo(
        address: String,
        lifecycle: Lifecycle,
        openCollatorInfo: (Collator) -> Unit,
        openValidatorInfo: (Validator) -> Unit
    ) {
        val asset = sharedState.assetWithChain.first().asset
        when (asset.staking) {
            Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Asset.StakingType.RELAYCHAIN -> {
                val validators = getValidators(lifecycle)
                val validator = validators.find { it.address == address } ?: error("cannot find validator")
                openValidatorInfo(validator)
            }

            Asset.StakingType.PARACHAIN -> {
                val collators = getCollators(lifecycle)
                val collator = collators[address] ?: error("cannot find collator")
                openCollatorInfo(collator)
            }
        }
    }

    suspend fun getIcon(address: String, sizeInDp: Int, type: Asset.StakingType): PictureDrawable {
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
