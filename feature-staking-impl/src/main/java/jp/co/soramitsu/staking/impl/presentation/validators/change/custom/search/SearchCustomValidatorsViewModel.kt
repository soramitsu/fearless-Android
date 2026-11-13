package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator.Companion.SIZE_MEDIUM
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

sealed class SearchBlockProducersState {
    object NoInput : SearchBlockProducersState()

    object Loading : SearchBlockProducersState()

    object NoResults : SearchBlockProducersState()

    object RefreshRequired : SearchBlockProducersState()

    class Failed(val message: String) : SearchBlockProducersState()

    class Success(val blockProducers: List<SearchBlockProducerModel>, val headerTitle: String) : SearchBlockProducersState()
}

private sealed class ProducerCatalogue {
    object Loading : ProducerCatalogue()
    object RefreshRequired : ProducerCatalogue()
    class Ready(val models: List<SearchBlockProducerModel>) : ProducerCatalogue()
    class Failed(val message: String) : ProducerCatalogue()
}

@HiltViewModel
class SearchCustomValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val sharedStateSetup: SetupStakingSharedState,
    @Named("StakingTokenUseCase") tokenUseCase: TokenUseCase,
    private val searchCustomBlockProducerInteractor: SearchCustomBlockProducerInteractor,
    private val interactor: StakingInteractor
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

    private val catalogueRetry = MutableStateFlow(0L)

    private val allBlockProducers = combine(
        selectedBlockProducers, catalogueRetry, searchCustomBlockProducerInteractor.observeSelectionContext()
    ) { selected, _, _ -> selected }
        .transformLatest<Set<String>, ProducerCatalogue> { selected ->
            emit(ProducerCatalogue.Loading)
            try {
                val validators = searchCustomBlockProducerInteractor.getBlockProducers(router.currentStackEntryLifecycle)
                val type = stakingType.first()
                val models = validators.toSet().map { it.toModel(it.address in selected, type) }
                emit(ProducerCatalogue.Ready(models))
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                emit(ProducerCatalogue.RefreshRequired)
            } catch (error: Exception) {
                val message = error.message ?: resourceManager.getString(R.string.common_error_general_message)
                emit(ProducerCatalogue.Failed(message))
                withContext(Dispatchers.Main.immediate) {
                    showError(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = message,
                        positiveButtonText = resourceManager.getString(R.string.common_retry),
                        positiveClick = ::retryCatalogue
                    )
                }
            }
        }.inBackground().stateIn(viewModelScope, SharingStarted.Eagerly, ProducerCatalogue.Loading)

    val screenState = combine(enteredQuery, allBlockProducers) { query, state -> query to state }
        .mapLatest { (query, state) ->
            when (state) {
                ProducerCatalogue.Loading -> SearchBlockProducersState.Loading
                ProducerCatalogue.RefreshRequired -> SearchBlockProducersState.RefreshRequired
                is ProducerCatalogue.Failed -> SearchBlockProducersState.Failed(state.message)
                is ProducerCatalogue.Ready -> searchResults(query, state.models)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchBlockProducersState.Loading)

    fun retryCatalogue() {
        catalogueRetry.update { it + 1 }
    }

    private suspend fun searchResults(
        query: String,
        blockProducers: List<SearchBlockProducerModel>
    ): SearchBlockProducersState {
        if (query.isEmpty()) return SearchBlockProducersState.NoInput
        val queryLower = query.lowercase(Locale.getDefault())
        val localResults = blockProducers.filter {
            it.address.startsWith(query) || it.name.lowercase(Locale.getDefault()).contains(queryLower)
        }
        val results = localResults.ifEmpty {
            try {
                searchCustomBlockProducerInteractor.getBlockProducerByAddress(query)?.let {
                    listOf(it.toModel(it.address in selectedBlockProducers.value, stakingType.first()))
                } ?: emptyList()
            } catch (error: Exception) {
                showSearchFailure(error)
                emptyList()
            }
        }
        return if (results.isEmpty()) {
            SearchBlockProducersState.NoResults
        } else {
            SearchBlockProducersState.Success(
                blockProducers = results,
                headerTitle = resourceManager.getString(R.string.common_search_results_number, results.size)
            )
        }
    }

    fun blockProducerClicked(validatorModel: SearchBlockProducerModel) {
        launch {
            try {
                searchCustomBlockProducerInteractor.blockProducerSelected(
                    validatorModel.address, sharedStateSetup, router.currentStackEntryLifecycle
                ).getOrThrow()
            } catch (error: Exception) {
                showSearchFailure(error)
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
            try {
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
                        interactor.validatorDetailsCache.update { prev ->
                            prev + (it.accountIdHex to mapValidatorToValidatorDetailsParcelModel(it))
                        }
                        router.openValidatorDetails(it.accountIdHex)
                    }
                )
            } catch (error: Exception) {
                showSearchFailure(error)
            }
        }
    }

    private fun showSearchFailure(error: Exception) {
        when (error) {
            is CancellationException -> throw error
            is BlockedValidatorException -> showError(resourceManager.getString(R.string.staking_custom_blocked_warning))
            else -> if (error.message == null) {
                showError(resourceManager.getString(R.string.common_error_general_message))
            } else {
                showError(error)
            }
        }
    }

    private suspend fun SearchCustomBlockProducerInteractor.BlockProducer.toModel(
        selected: Boolean,
        type: Asset.StakingType
    ): SearchBlockProducerModel {
        return SearchBlockProducerModel(name, address, selected, rewardsPercent, searchCustomBlockProducerInteractor.getIcon(address, SIZE_MEDIUM, type))
    }
}
