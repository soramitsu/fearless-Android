package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.Delegation
import jp.co.soramitsu.staking.api.domain.model.DelegatorState
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SubqueryRewardCalculator
import jp.co.soramitsu.staking.impl.domain.validators.CollatorProvider
import jp.co.soramitsu.staking.impl.presentation.mappers.toCollator
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger

internal class CollatorCatalogueFixture {
    val chainId = "collator-contract"
    val ids = (1..3).map { ByteArray(20) { _ -> it.toByte() } }
    val hex = ids.map { it.toHexString() }
    val accountId = ByteArray(20) { 9 }
    val asset = mock<Asset> {
        on { id } doReturn "para"
        on { staking } doReturn Asset.StakingType.PARACHAIN
        on { chainId } doReturn this@CollatorCatalogueFixture.chainId
    }
    val chain = mock<Chain> {
        on { id } doReturn chainId
        on { name } doReturn "Test parachain"
        on { ecosystem } doReturn Ecosystem.EthereumBased
        on { assets } doReturn listOf(asset)
    }
    val wallet = mock<MetaAccount> {
        on { ethereumAddress } doReturn accountId
        on { chainAccounts } doReturn emptyMap()
    }
    val accounts = mock<AccountRepository>()
    val repository = mock<StakingParachainScenarioRepository>()
    val identities = mock<IdentityRepository>()
    val rewards = mock<RewardCalculatorFactory>()
    val calculator = mock<SubqueryRewardCalculator>()
    val constants = mock<StakingConstantsRepository>()
    val provider = CollatorProvider(repository, identities, rewards, constants, accounts)
    val identity = RootIdentity("Collator Alpha", null, null, null, null, null, null, null)
    val candidate = CandidateInfo(
        BigInteger("300"), BigInteger("4"), BigInteger("1900"), BigInteger("500"),
        BigInteger("400"), BigInteger("100"), CandidateCapacity.Full, CandidateCapacity.Partial,
        "pending bond", CandidateInfoStatus.ACTIVE
    )

    suspend fun defaults() {
        whenever(accounts.getSelectedMetaAccount()).thenReturn(wallet)
        whenever(repository.getDelegatorState(chainId, accountId)).thenReturn(null)
        whenever(repository.getSelectedCandidates(chainId)).thenReturn(ids)
        whenever(repository.getCandidateInfos(any(), any())).thenAnswer { invocation ->
            val requested = invocation.getArgument<List<ByteArray>>(1)
            requested.associate { it.toHexString() to candidate }
        }
        whenever(identities.getIdentitiesFromIdsBytes(any(), any())).thenReturn(mapOf(hex[0] to identity))
        whenever(rewards.createSubquery()).thenReturn(calculator)
        whenever(calculator.getApy(any())).thenReturn(mapOf(hex[0] to BigDecimal("12.5")))
        whenever(constants.maxTopDelegationsPerCandidate(chainId)).thenReturn(4)
        whenever(constants.maxBottomDelegationsPerCandidate(chainId)).thenReturn(4)
    }

    suspend fun used(vararg indices: Int) {
        whenever(repository.getDelegatorState(chainId, accountId)).thenReturn(
            DelegatorState(
                accountId, indices.map { Delegation(ids[it].copyOf(), BigInteger("500")) },
                BigInteger("1000"), DelegatorStateStatus.ACTIVE
            )
        )
    }

    fun collator(index: Int = 0, apy: BigDecimal? = BigDecimal("12.5")) =
        candidate.toCollator(hex[index], if (index == 0) identity else null, apy)
}
