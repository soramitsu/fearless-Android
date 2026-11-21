package jp.co.soramitsu.staking.impl.presentation.validators

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.recommendations.CollatorRecommendator
import jp.co.soramitsu.staking.impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendator
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.SelectBlockProducersStep
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
abstract class LegacySelectionFixture {
    protected val router = mockk<StakingRouter>(relaxed = true)
    protected val resources = mockk<ResourceManager>(relaxed = true)
    protected val icons = mockk<AddressIconGenerator>()
    protected val interactor = mockk<StakingInteractor>(relaxed = true)
    protected val relay = mockk<StakingRelayChainScenarioInteractor>()
    protected val parachain = mockk<StakingParachainScenarioInteractor>()
    protected val tokens = mockk<TokenUseCase>()
    protected val validatorFactory = mockk<ValidatorRecommendatorFactory>()
    protected val collatorFactory = mockk<CollatorRecommendatorFactory>()
    protected val settingsFactory = mockk<RecommendationSettingsProviderFactory>()
    protected val settings = SettingsStorage()
    protected val shared = SetupStakingSharedState()
    protected val validatorSettings = RecommendationSettingsProvider.RelayChain(256, 2)
    protected val collatorSettings = RecommendationSettingsProvider.Parachain(256, 2)
    protected val cache = MutableStateFlow<Map<String, ValidatorDetailsParcelModel>>(emptyMap())
    protected val alpha = validator("01", "Alpha", "0.2")
    protected val beta = validator("02", "Beta", "0.1")
    protected val alice = collator("01", "Alice", "0.2")
    protected val bob = collator("02", "Bob", "0.1")
    private val models = mutableListOf<ViewModel>()

    private fun validator(byte: String, name: String, apy: String) = Validator(
        "$byte-address", false, byte.repeat(32), ValidatorPrefs(BigDecimal("0.05"), false),
        Validator.ElectedInfo(StakingLaneFixtures.planks(100), StakingLaneFixtures.planks(10), emptyList(), BigDecimal(apy), false),
        RootIdentity(name, null, null, null, null, null, null, null)
    )

    private fun collator(byte: String, name: String, apy: String) = Collator(
        "0x" + byte.repeat(20), StakingLaneFixtures.planks(10), BigInteger.TWO, StakingLaneFixtures.planks(20),
        BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO, CandidateCapacity.Empty, CandidateCapacity.Empty, null,
        CandidateInfoStatus.ACTIVE, RootIdentity(name, null, null, null, null, null, null, null), BigDecimal(apy)
    )

    @Before
    fun prepareSelectionDependencies() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "s${firstArg<Int>()}:${secondArg<Array<out Any>>().joinToString()}" }
        every { interactor.selectedChainFlow() } returns flowOf(StakingLaneFixtures.chain())
        every { interactor.currentAssetFlow() } returns flowOf(StakingLaneFixtures.asset())
        every { interactor.validatorDetailsCache } returns cache
        every { tokens.currentTokenFlow() } returns flowOf(StakingLaneFixtures.token())
        coEvery { tokens.currentToken() } returns StakingLaneFixtures.token()
        coEvery { relay.maxValidatorsPerNominator() } returns 2
        coEvery { parachain.maxDelegationsPerDelegator() } returns 2
        coEvery { icons.createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { icons.createEthereumAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
        coEvery { settingsFactory.createRelayChain(any()) } returns validatorSettings
        coEvery { settingsFactory.createParachain(any()) } returns collatorSettings
        coEvery { validatorFactory.create(any()) } returns ValidatorRecommendator(listOf(alpha, beta))
        coEvery { collatorFactory.create(any()) } returns CollatorRecommendator(listOf(alice, bob))
    }

    @After
    fun releaseSelectionModels() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    protected fun <T : ViewModel> track(model: T): T = model.also { models.add(it) }
    protected suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean = { true }): T = withTimeout(5_000) { first(predicate) }
    protected fun ready(validators: List<Validator> = listOf(alpha), method: ReadyToSubmit.SelectionMethod = ReadyToSubmit.SelectionMethod.CUSTOM) =
        ReadyToSubmit.Stash(ReadyToSubmit.Payload.Full(BigDecimal.TEN, RewardDestination.Restake, "account", validators, method))
    protected fun collatorStep() = SelectBlockProducersStep.Collators(SelectBlockProducersStep.Payload.Parachain(BigDecimal.TEN, "account"))
}
