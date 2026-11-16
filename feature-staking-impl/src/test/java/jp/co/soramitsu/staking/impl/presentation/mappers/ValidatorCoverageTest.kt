package jp.co.soramitsu.staking.impl.presentation.mappers

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.view.Error
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorPrefsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorStakeParcelModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidatorCoverageTest {

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val token = StakingLaneFixtures.token(chainAsset)
    private val fiatToken = StakingLaneFixtures.token(chainAsset, BigDecimal("2"))
    private val drawable = PictureDrawable(Picture())
    private val iconGenerator = mockk<AddressIconGenerator>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val nominator = IndividualExposure(ByteArray(32) { 8 }, StakingLaneFixtures.planks(3))

    private val elected = Validator(
        address = "unused",
        slashed = false,
        accountIdHex = "01".repeat(32),
        prefs = ValidatorPrefs(BigDecimal("0.05"), false),
        electedInfo = Validator.ElectedInfo(
            totalStake = StakingLaneFixtures.planks(100),
            ownStake = StakingLaneFixtures.planks(10),
            nominatorStakes = listOf(nominator),
            apy = BigDecimal("0.15"),
            isOversubscribed = true
        ),
        identity = RootIdentity("Alpha", "Legal", null, null, null, null, null, null)
    )

    private val waiting = Validator("unused", true, "02".repeat(32), null, null, null)

    @Before
    fun setUp() {
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        coEvery { iconGenerator.createAddressIcon(any<ByteArray>(), any(), any()) } returns drawable
    }

    @Test
    fun validatorModelUsesCreatedIconAndApyScoring() {
        val model = runBlocking {
            mapValidatorToValidatorModel(chain, elected, { AddressModel(it, drawable, "Alpha") }, token, isChecked = true)
        }

        assertEquals(chain.addressOf(elected.accountIdHex.fromHex()), model.address)
        assertEquals("Alpha", model.title)
        assertSame(drawable, model.image)
        assertEquals(true, model.isChecked)
        assertSame(elected, model.validator)
        assertEquals("15%", (model.scoring as ValidatorModel.Scoring.OneField).field)
    }

    @Test
    fun iconGeneratorOverloadNamesValidatorByIdentity() {
        val model = runBlocking {
            mapValidatorToValidatorModel(chain, elected, iconGenerator, token, sorting = BlockProducersSorting.ValidatorSorting.TotalStakeSorting)
        }

        assertEquals("Alpha", model.title)
        assertNull(model.isChecked)
        val scoring = model.scoring as ValidatorModel.Scoring.TwoFields
        assertTrue(scoring.primary, scoring.primary.contains("100") && scoring.primary.contains("DOT"))
        assertNull(scoring.secondary)
    }

    @Test
    fun stakeScoringsIncludeFiatWhenRateKnown() {
        val total = BlockProducersSorting.ValidatorSorting.TotalStakeSorting.toScoring(elected, fiatToken) as ValidatorModel.Scoring.TwoFields
        val own = BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting.toScoring(elected, fiatToken) as ValidatorModel.Scoring.TwoFields
        val apy = BlockProducersSorting.ValidatorSorting.APYSorting.toScoring(waiting, token) as ValidatorModel.Scoring.OneField

        assertTrue(total.secondary!!, total.secondary!!.contains("200"))
        assertTrue(own.primary, own.primary.contains("10") && own.primary.contains("DOT"))
        assertTrue(own.secondary!!, own.secondary!!.contains("20"))
        assertEquals("0%", apy.field)
    }

    @Test(expected = IllegalStateException::class)
    fun collatorSortingCannotScoreValidators() {
        @Suppress("UNCHECKED_CAST")
        val sorting = (BlockProducersSorting.CollatorSorting.APYSorting as BlockProducersSorting<*>) as BlockProducersSorting<Validator>
        sorting.toScoring(elected, token)
    }

    @Test
    fun electedValidatorMapsToActiveParcel() {
        val parcel = mapValidatorToValidatorDetailsParcelModel(elected)

        assertEquals(elected.accountIdHex, parcel.accountIdHex)
        val stake = parcel.stake as ValidatorStakeParcelModel.Active
        assertEquals(StakingLaneFixtures.planks(100), stake.totalStake)
        assertEquals(StakingLaneFixtures.planks(10), stake.ownStake)
        assertEquals(BigDecimal("0.15"), stake.apy)
        assertTrue(stake.isOversubscribed)
        assertEquals(false, stake.isSlashed)
        assertNull(stake.nominatorInfo)
        assertEquals(StakingLaneFixtures.planks(3), stake.nominators.single().value)
        assertTrue(stake.nominators.single().who.contentEquals(nominator.who))
        assertEquals("Alpha", parcel.identity?.display)
        assertEquals("Legal", parcel.identity?.legal)
        assertEquals(BigDecimal("0.05"), parcel.prefs?.commission)
        assertEquals(false, parcel.prefs?.isBlocked)
    }

    @Test
    fun waitingValidatorMapsToInactiveParcel() {
        val parcel = mapValidatorToValidatorDetailsParcelModel(waiting)

        assertSame(ValidatorStakeParcelModel.Inactive, parcel.stake)
        assertNull(parcel.identity)
        assertNull(parcel.prefs)
    }

    @Test
    fun nominatedStatusCarriesRewardFlag() {
        val active = NominatedValidator(elected, NominatedValidator.Status.Active(BigInteger.ONE, willUserBeRewarded = true))
        val electedOnly = NominatedValidator(elected, NominatedValidator.Status.Elected)

        val activeStake = mapValidatorToValidatorDetailsWithStakeFlagParcelModel(active).stake as ValidatorStakeParcelModel.Active
        val electedStake = mapValidatorToValidatorDetailsWithStakeFlagParcelModel(electedOnly).stake as ValidatorStakeParcelModel.Active

        assertEquals(true, activeStake.nominatorInfo?.willBeRewarded)
        assertNull(electedStake.nominatorInfo)
    }

    private fun activeParcel(
        oversubscribed: Boolean,
        slashed: Boolean,
        rewarded: Boolean?,
        blocked: Boolean = false
    ) = ValidatorDetailsParcelModel(
        accountIdHex = elected.accountIdHex,
        stake = ValidatorStakeParcelModel.Active(
            totalStake = StakingLaneFixtures.planks(100),
            ownStake = StakingLaneFixtures.planks(10),
            nominators = emptyList(),
            apy = BigDecimal("0.15"),
            isSlashed = slashed,
            isOversubscribed = oversubscribed,
            nominatorInfo = rewarded?.let { ValidatorStakeParcelModel.Active.NominatorInfo(it) }
        ),
        identity = null,
        prefs = ValidatorPrefsParcelModel(BigDecimal("0.05"), blocked)
    )

    @Test
    fun errorsDescribeOversubscriptionSlashAndBlock() {
        assertEquals(listOf(Error.OversubscribedPaid), mapValidatorDetailsToErrors(activeParcel(true, false, true)))
        assertEquals(listOf(Error.OversubscribedUnpaid), mapValidatorDetailsToErrors(activeParcel(true, false, null)))
        assertEquals(listOf(Error.Blocked, Error.Slashed), mapValidatorDetailsToErrors(activeParcel(false, true, null, blocked = true)))
        assertEquals(emptyList<Error>(), mapValidatorDetailsToErrors(activeParcel(false, false, false)))
        assertNull(mapValidatorDetailsToErrors(mapValidatorToValidatorDetailsParcelModel(waiting)))
    }

    @Test
    fun activeDetailsModelFormatsStakeAndIdentity() {
        val parcel = mapValidatorToValidatorDetailsParcelModel(elected)

        val model = runBlocking { mapValidatorDetailsParcelToValidatorDetailsModel(chain, parcel, asset, 256, iconGenerator, resources) }

        assertEquals(chain.addressOf(elected.accountIdHex.fromHex()), model.address)
        assertSame(drawable, model.addressImage)
        assertEquals("Alpha", model.identity?.display)
        assertEquals(false, model.identity?.isEmpty)
        assertEquals(false, model.identity?.isEmptyExceptName)
        assertEquals("s${R.string.staking_nominator_status_active}", model.stake.statusText)
        assertEquals(R.color.green, model.stake.statusColorRes)
        assertEquals("5%", model.stake.prefs?.commission)
        assertEquals(false, model.stake.prefs?.isBlocked)
        val active = model.stake.activeStakeModel!!
        assertTrue(active.totalStake, active.totalStake.contains("100") && active.totalStake.contains("DOT"))
        assertNull(active.totalStakeFiat)
        assertEquals("1", active.nominatorsCount)
        assertEquals("15%", active.apy)
        assertEquals("256", active.maxNominations)
    }

    @Test
    fun inactiveDetailsModelShowsUnelectedStatus() {
        val parcel = mapValidatorToValidatorDetailsParcelModel(waiting)

        val model = runBlocking { mapValidatorDetailsParcelToValidatorDetailsModel(chain, parcel, asset, null, iconGenerator, resources) }

        assertEquals("s${R.string.staking_validator_status_unelected}", model.stake.statusText)
        assertEquals(R.color.gray2, model.stake.statusColorRes)
        assertNull(model.stake.activeStakeModel)
        assertNull(model.stake.prefs)
        assertNull(model.identity)
    }

    @Test
    fun identityMappersCopyEveryField() {
        val identity = RootIdentity("d", "l", "w", "r", "e", "p", "i", "t")

        val model = mapIdentityParcelModelToIdentityModel(mapIdentityToIdentityParcelModel(identity))

        assertEquals(listOf("d", "l", "w", "r", "e", "p", "i", "t"), listOf(model.display, model.legal, model.web, model.riot, model.email, model.pgpFingerprint, model.image, model.twitter))
        assertEquals(BigDecimal(100), PERCENT_MULTIPLIER)
    }
}
