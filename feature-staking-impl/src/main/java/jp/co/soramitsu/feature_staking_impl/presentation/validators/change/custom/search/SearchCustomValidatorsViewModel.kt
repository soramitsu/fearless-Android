package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.search

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator.Companion.SIZE_MEDIUM
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.withLoadingSingle
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.search.BlockedValidatorException
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.search.SearchCustomBlockProducerInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

sealed class SearchBlockProducersState {
    object NoInput : SearchBlockProducersState()

    object Loading : SearchBlockProducersState()

    object NoResults : SearchBlockProducersState()

    class Success(val blockProducers: List<SearchBlockProducerModel>, val headerTitle: String) : SearchBlockProducersState()
}

@HiltViewModel
class SearchCustomValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    @Named("StakingTokenUseCase") tokenUseCase: TokenUseCase,
    private val searchCustomBlockProducerInteractor: SearchCustomBlockProducerInteractor
) : BaseViewModel() {

    private val confirmSetupState = sharedStateSetup.setupStakingProcess
        .filterIsInstance<SetupStakingProcess.ReadyToSubmit<*>>()
        .share()

    private val selectedBlockProducers = confirmSetupState
        .map { it.payload.blockProducers.map { producer -> producer.address }.toSet() }
        .inBackground()
        .share()

    private val currentTokenFlow = tokenUseCase.currentTokenFlow()
        .share()

    val enteredQuery = MutableStateFlow("")

    private val allBlockProducers by lazy {
        async { searchCustomBlockProducerInteractor.getBlockProducers(router.currentStackEntryLifecycle).toSet() }
    }

    private val foundBlockProducersState = enteredQuery
        .withLoadingSingle { query ->
            if (query.isNotEmpty()) {
                searchCustomBlockProducerInteractor.searchBlockProducer(
                    query,
                    allBlockProducers.invoke()
                )
            } else {
                null
            }
        }
        .inBackground()
        .share()

    private val selectedBlockProducersModelsState = combine(
        selectedBlockProducers,
        foundBlockProducersState
    ) { selectedBlockProducers, foundBlockProducersState ->
        foundBlockProducersState.map { blockProducers ->
            blockProducers?.map { blockProducer ->
                blockProducer.toModel(blockProducer.address in selectedBlockProducers)
            }
        }
    }
        .inBackground()
        .share()

    val screenState = selectedBlockProducersModelsState.map { blockProducersState ->
        when {
            blockProducersState is LoadingState.Loading -> SearchBlockProducersState.Loading
            blockProducersState is LoadingState.Loaded && blockProducersState.data == null -> SearchBlockProducersState.NoInput

            blockProducersState is LoadingState.Loaded && blockProducersState.data.isNullOrEmpty().not() -> {
                val blockProducers = blockProducersState.data!!

                SearchBlockProducersState.Success(
                    blockProducers = blockProducers,
                    headerTitle = resourceManager.getString(R.string.common_search_results_number, blockProducers.size)
                )
            }

            else -> SearchBlockProducersState.NoResults
        }
    }.share()

    fun blockProducerClicked(validatorModel: SearchBlockProducerModel) {
        launch {
            val result = searchCustomBlockProducerInteractor.blockProducerSelected(validatorModel.address, sharedStateSetup, router.currentStackEntryLifecycle)
            if (result.isFailure && result.exceptionOrNull() is BlockedValidatorException) {
                showError(resourceManager.getString(R.string.staking_custom_blocked_warning))
            }
        }
    }

    fun backClicked() {
        router.back()
    }

    fun doneClicked() {
        router.back()
    }

    fun blockProducerInfoClicked(validatorModel: SearchBlockProducerModel) {
        launch {
            searchCustomBlockProducerInteractor.navigateBlockProducerInfo(
                validatorModel.address, router.currentStackEntryLifecycle,
                {
                    router.openCollatorDetails(
                        CollatorDetailsParcelModel(
                            it.address.requireHexPrefix().fromHex().toHexString(true),
                            CollatorStakeParcelModel(
                                status = it.status,
                                selfBonded = it.bond,
                                delegations = it.delegationCount.toInt(),
                                totalStake = it.totalCounted,
                                minBond = it.lowestTopDelegationAmount,
                                estimatedRewards = it.apy,
                            ),
                            it.identity?.let { identity ->
                                IdentityParcelModel(
                                    display = identity.display,
                                    legal = identity.legal,
                                    web = identity.web,
                                    riot = identity.riot,
                                    email = identity.email,
                                    pgpFingerprint = identity.pgpFingerprint,
                                    image = identity.image,
                                    twitter = identity.twitter,
                                )
                            },
                            it.request.orEmpty(),
                        )
                    )
                },
                {
                    router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(it))
                }
            )
        }
    }

    private suspend fun SearchCustomBlockProducerInteractor.BlockProducer.toModel(
        selected: Boolean
    ): SearchBlockProducerModel {
        return SearchBlockProducerModel(name, address, selected, rewardsPercent, searchCustomBlockProducerInteractor.getIcon(address, SIZE_MEDIUM))
    }
}
