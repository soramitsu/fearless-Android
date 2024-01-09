package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.common.address.AddressIconGenerator.Companion.SIZE_MEDIUM
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.staking.impl.domain.validators.current.search.BlockedValidatorException
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val stakingType = tokenUseCase.currentTokenFlow().map {
        it.configuration.staking
    }

    private val selectedBlockProducers = confirmSetupState
        .map { it.payload.blockProducers.map { producer -> producer.address }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val enteredQuery = MutableStateFlow("")

    private val allBlockProducers = selectedBlockProducers.map { selected ->
        val validators = searchCustomBlockProducerInteractor.getBlockProducers(router.currentStackEntryLifecycle)
        val type = stakingType.first()
        val models = validators.toSet().map { blockProducer ->
            blockProducer.toModel(blockProducer.address in selected, type)
        }
        LoadingState.Loaded(models)
    }.inBackground().stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    private val filteredBlockProducers = combine(enteredQuery, allBlockProducers) { query, blockProducersState ->
        blockProducersState.map { blockProducers ->
            if (query.isNotEmpty()) {
                val queryLower = query.lowercase(Locale.getDefault())

                val searchResults = blockProducers.asSequence().filter {
                    val foundInIdentity = it.name.lowercase(Locale.getDefault()).contains(queryLower)
                    it.address.startsWith(query) || foundInIdentity
                }.toList()

                searchResults.ifEmpty {
                    searchCustomBlockProducerInteractor.getBlockProducerByAddress(queryLower)?.let {
                        listOf(it.toModel(it.address in selectedBlockProducers.value, stakingType.first()))
                    } ?: emptyList()
                }
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    val screenState = filteredBlockProducers.map { blockProducersState ->
        when {
            blockProducersState is LoadingState.Loading -> SearchBlockProducersState.Loading
            blockProducersState is LoadingState.Loaded && blockProducersState.data == null -> SearchBlockProducersState.NoInput
            blockProducersState is LoadingState.Loaded && blockProducersState.data?.isEmpty() == true -> SearchBlockProducersState.NoResults
            blockProducersState is LoadingState.Loaded && blockProducersState.data.isNullOrEmpty().not() -> {
                val blockProducers = blockProducersState.data!!

                SearchBlockProducersState.Success(
                    blockProducers = blockProducers,
                    headerTitle = resourceManager.getString(R.string.common_search_results_number, blockProducers.size)
                )
            }

            else -> SearchBlockProducersState.NoInput
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchBlockProducersState.NoResults)

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
                validatorModel.address,
                router.currentStackEntryLifecycle,
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
                                estimatedRewards = it.apy
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
                                    twitter = identity.twitter
                                )
                            },
                            it.request.orEmpty()
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
        selected: Boolean,
        type: Asset.StakingType
    ): SearchBlockProducerModel {
        return SearchBlockProducerModel(name, address, selected, rewardsPercent, searchCustomBlockProducerInteractor.getIcon(address, SIZE_MEDIUM, type))
    }
}
