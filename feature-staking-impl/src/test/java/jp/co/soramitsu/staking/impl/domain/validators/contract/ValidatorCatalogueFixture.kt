package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.rewards.ManualRewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorSource
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger

internal class ValidatorCatalogueFixture(val chainId: String = "validator-contract") {
    val ids = (1..3).map { ByteArray(32) { _ -> it.toByte() } }
    val hex = ids.map { it.toHexString() }
    val addresses = ids.map { it.toAddress(42) }
    val asset = mock<Asset> {
        on { id } doReturn "dot"
        on { chainId } doReturn this@ValidatorCatalogueFixture.chainId
        on { isUtility } doReturn true
        on { staking } doReturn Asset.StakingType.RELAYCHAIN
    }
    val chain = mock<Chain> {
        on { id } doReturn chainId
        on { name } doReturn "Test relay"
        on { addressPrefix } doReturn 42
        on { ecosystem } doReturn Ecosystem.Substrate
        on { assets } doReturn listOf(asset)
    }
    val repository = mock<StakingRelayChainScenarioRepository>()
    val identities = mock<IdentityRepository>()
    val rewards = mock<RewardCalculatorFactory>()
    val calculator = mock<ManualRewardCalculator>()
    val constants = mock<StakingConstantsRepository>()
    val provider = ValidatorProvider(repository, identities, rewards, constants)
    val identity = RootIdentity("Alice", null, null, null, null, null, null, null)
    val prefs = ValidatorPrefs(BigDecimal("0.125"), false)
    val exposure = LegacyExposure(
        BigInteger("1200"), BigInteger("300"),
        listOf(IndividualExposure(ids[1], BigInteger("400")), IndividualExposure(ids[2], BigInteger("500")))
    )

    suspend fun defaults() {
        whenever(repository.getAllValidatorPrefs(chainId)).thenReturn(linkedMapOf(hex[0] to prefs, hex[1] to prefs))
        whenever(repository.getLegacyActiveElectedValidatorsExposures(chainId)).thenReturn(mapOf(hex[0] to exposure))
        whenever(repository.getSlashes(any(), any())).thenReturn(mapOf(hex[0] to true))
        whenever(identities.getIdentitiesFromIds(any(), any())).thenReturn(mapOf(hex[0] to identity))
        whenever(constants.maxRewardedNominatorPerValidator(chainId)).thenReturn(2)
        whenever(rewards.createManual(any(), any())).thenReturn(calculator)
        whenever(rewards.createSora(any(), any(), anyOrNull())).thenReturn(calculator)
        whenever(rewards.createReef(any(), any(), anyOrNull())).thenReturn(calculator)
        whenever(rewards.createTernoa(any(), any())).thenReturn(calculator)
        whenever(calculator.getApyFor(any())).thenReturn(BigDecimal("0.125"))
        whenever(repository.getValidatorPrefs(chainId, listOf(addresses[0].toHexAccountId())))
            .thenReturn(mapOf(hex[0] to prefs))
    }

    suspend fun elected() = provider.getValidators(chain, ValidatorSource.Elected)

    fun validator(
        index: Int = 0,
        blocked: Boolean = false,
        apy: BigDecimal? = BigDecimal("0.125")
    ) = Validator(
        addresses[index], false, hex[index], ValidatorPrefs(BigDecimal("0.1"), blocked),
        apy?.let { Validator.ElectedInfo(exposure.total, exposure.own, exposure.others, it, false) },
        if (index == 0) identity else null
    )
}

internal fun assertCatalogueFailure(expected: Throwable, actual: Throwable?) {
    assertEquals(expected.javaClass, actual?.javaClass)
    assertEquals(expected.message, actual?.message)
    assertTrue(generateSequence(actual) { it.cause }.any { it === expected })
}
