package jp.co.soramitsu.feature_staking_impl.domain.validators.current.search

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle
import java.math.BigDecimal
import java.util.Locale
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.toggle
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validators.CollatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorSource
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setCustomCollators
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setCustomValidators
import jp.co.soramitsu.runtime.ext.isValidAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val ELECTED_COLLATORS_CACHE = "ELECTED_COLLATORS_CACHE"
private const val ELECTED_VALIDATORS_CACHE = "ELECTED_VALIDATORS_CACHE"

class SearchCustomBlockProducerInteractor(
    private val collatorProvider: CollatorProvider,
    private val validatorProvider: ValidatorProvider,
    private val sharedState: StakingSharedState,
    private val computationalCache: ComputationalCache
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
            Chain.Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Chain.Asset.StakingType.RELAYCHAIN -> {
                getValidators(lifecycle).map {
                    BlockProducer(
                        it.identity?.display ?: it.address,
                        it.address,
                        (it.electedInfo?.apy ?: BigDecimal.ZERO).fractionToPercentage().formatAsPercentage()
                    )
                }
            }
            Chain.Asset.StakingType.PARACHAIN -> {
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

    @SuppressLint("DefaultLocale")
    suspend fun searchBlockProducer(query: String, localValidators: Collection<BlockProducer>): List<BlockProducer> = withContext(Dispatchers.Default) {
        val queryLower = query.lowercase(Locale.getDefault())

        val searchInLocal = localValidators.filter {
            val foundInIdentity = it.name.lowercase(Locale.getDefault()).contains(queryLower)
            it.address.startsWith(query) || foundInIdentity
        }

        if (searchInLocal.isNotEmpty()) {
            return@withContext searchInLocal
        }

        val (chain, asset) = sharedState.assetWithChain.first()

        if (chain.isValidAddress(query)) {
            when (asset.staking) {
                Chain.Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
                Chain.Asset.StakingType.RELAYCHAIN -> {
                    val validator = validatorProvider.getValidatorWithoutElectedInfo(chain, query)
                    if (validator.prefs != null) {
                        listOf(
                            BlockProducer(
                                validator.identity?.display ?: validator.address,
                                validator.address,
                                (validator.electedInfo?.apy ?: BigDecimal.ZERO).fractionToPercentage().formatAsPercentage(),
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
                Chain.Asset.StakingType.PARACHAIN -> {
                    val collator = collatorProvider.getCollators(chain)[query.requireHexPrefix()]
                    collator?.let {
                        listOf(
                            BlockProducer(
                                it.identity?.display ?: it.address,
                                it.address,
                                (it.apy ?: BigDecimal.ZERO).formatAsPercentage()
                            )
                        )
                    } ?: emptyList()
                }
            }
        } else {
            emptyList()
        }
    }

    suspend fun blockProducerSelected(address: String, setupStakingProcess: SetupStakingSharedState, lifecycle: Lifecycle): Result<Unit> {
        val asset = sharedState.assetWithChain.first().asset
        when (asset.staking) {
            Chain.Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Chain.Asset.StakingType.RELAYCHAIN -> {
                val validators = getValidators(lifecycle)
                val selectedValidator = validators.find { it.address == address } ?: error("cannot find validator")
                if (selectedValidator.prefs?.blocked == true) return Result.failure(BlockedValidatorException)
                val selectedValidators =
                    setupStakingProcess.get<SetupStakingProcess.ReadyToSubmit.Stash>().payload.blockProducers.toSet().toggle(selectedValidator)
                setupStakingProcess.setCustomValidators(selectedValidators.toList())
                return Result.success(Unit)
            }
            Chain.Asset.StakingType.PARACHAIN -> {
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
            Chain.Asset.StakingType.UNSUPPORTED -> error("Wrong staking type")
            Chain.Asset.StakingType.RELAYCHAIN -> {
                val validators = getValidators(lifecycle)
                val validator = validators.find { it.address == address } ?: error("cannot find validator")
                openValidatorInfo(validator)
            }
            Chain.Asset.StakingType.PARACHAIN -> {
                val collators = getCollators(lifecycle)
                val collator = collators[address] ?: error("cannot find collator")
                openCollatorInfo(collator)
            }
        }
    }

    data class BlockProducer(val name: String, val address: String, val rewardsPercent: String, val selected: Boolean = false)
}

object BlockedValidatorException : Exception("This validator is not accepting nominations at this moment. Please, try again in the next era.")
