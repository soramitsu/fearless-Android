package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.ElectionStatus
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_api.domain.model.Nominations
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorSummary
import jp.co.soramitsu.feature_staking_impl.domain.model.StakingReward
import jp.co.soramitsu.feature_staking_impl.presentation.common.StashSetup
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

class StakingInteractor(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val stakingRewardsRepository: StakingRewardsRepository,
    private val substrateCalls: SubstrateCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
) {

    suspend fun syncStakingRewards(accountAddress: String) = withContext(Dispatchers.IO) {
        runCatching {
            stakingRewardsRepository.syncTotalRewards(accountAddress)
        }
    }

    suspend fun observeNominatorSummary(nominatorState: StakingState.Stash.Nominator): Flow<NominatorSummary> = withContext(Dispatchers.Default) {
        val networkType = nominatorState.accountAddress.networkType()
        val tokenType = Token.Type.fromNetworkType(networkType)

        combine(
            stakingRepository.electionStatusFlow(networkType),
            stakingRepository.observeActiveEraIndex(networkType),
            stakingRewardsRepository.stakingRewardsFlow(nominatorState.accountAddress),
            walletRepository.assetFlow(nominatorState.accountAddress, tokenType)
        ) { electionStatus, activeEraIndex, rewards, asset ->

            val eraStakers = stakingRepository.getElectedValidatorsExposure(activeEraIndex).values

            val status = when {
                electionStatus is ElectionStatus.Open -> NominatorSummary.Status.ELECTION
                isNominationActive(nominatorState.stashId, eraStakers) -> NominatorSummary.Status.ACTIVE
                isNominationWaiting(nominatorState.nominations, activeEraIndex) -> NominatorSummary.Status.WAITING
                else -> NominatorSummary.Status.INACTIVE
            }

            NominatorSummary(
                status = status,
                totalStaked = asset.bonded,
                totalRewards = totalRewards(rewards)
            )
        }
    }

    suspend fun observeNetworkInfoState(networkType: Node.NetworkType): Flow<NetworkInfo> {
        val lockupPeriod = stakingRepository.getLockupPeriodInDays(networkType)

        return stakingRepository.observeActiveEraIndex(networkType).map { eraIndex ->
            val exposures = stakingRepository.getElectedValidatorsExposure(eraIndex).values

            NetworkInfo(
                lockupPeriodInDays = lockupPeriod,
                minimumStake = minimumStake(exposures),
                totalStake = totalStake(exposures),
                nominatorsCount = activeNominators(exposures)
            )
        }
    }

    fun selectedAccountStakingState() = accountRepository.selectedAccountFlow()
        .flatMapLatest { stakingRepository.stakingStateFlow(it.address) }

    suspend fun getAccountsInCurrentNetwork() = withContext(Dispatchers.Default) {
        val account = accountRepository.getSelectedAccount()

        accountRepository.getAccountsByNetworkType(account.network.type)
            .map(::mapAccountToStakingAccount)
    }

    fun currentAssetFlow() = accountRepository.selectedAccountFlow()
        .flatMapLatest { walletRepository.assetsFlow(it.address) }
        .filter { it.isNotEmpty() }
        .map { it.first() }

    suspend fun getSelectedNetworkType(): Node.NetworkType {
        return accountRepository.getSelectedNode().networkType
    }

    fun selectedAccountFlow(): Flow<StakingAccount> {
        return accountRepository.selectedAccountFlow()
            .map { mapAccountToStakingAccount(it) }
    }

    suspend fun getAccount(address: String) = mapAccountToStakingAccount(accountRepository.getAccount(address))

    suspend fun getSelectedAccount(): StakingAccount = withContext(Dispatchers.Default) {
        val account = accountRepository.getSelectedAccount()

        mapAccountToStakingAccount(account)
    }

    suspend fun setupStaking(
        originAddress: String,
        amount: BigDecimal,
        tokenType: Token.Type,
        rewardDestination: RewardDestination,
        nominations: List<MultiAddress>,
        skipBond: Boolean,
    ) = withContext(Dispatchers.Default) {
        runCatching {
            val account = accountRepository.getAccount(originAddress)

            val extrinsic = extrinsicBuilderFactory.create(account).apply {
                if (!skipBond) {
                    bond(
                        controllerAddress = MultiAddress.Id(originAddress.toAccountId()),
                        amount = tokenType.planksFromAmount(amount),
                        payee = rewardDestination
                    )
                }

                nominate(nominations)
            }.build()

            substrateCalls.submitExtrinsic(extrinsic)
        }
    }

    suspend fun getExistingStashSetup(accountStakingState: StakingState.Stash.None, asset: Asset): StashSetup {
        val amount = asset.bonded
        val rewardDestination = stakingRepository.getRewardDestination(accountStakingState)

        return StashSetup(alreadyHasStash = true, amount, rewardDestination)
    }

    private fun totalRewards(rewards: List<StakingReward>) = rewards.sumByBigDecimal {
        it.amount * it.type.summingCoefficient.toBigDecimal()
    }

    private fun isNominationWaiting(nominations: Nominations, activeEraIndex: BigInteger): Boolean {
        return nominations.submittedInEra == activeEraIndex
    }

    private fun isNominationActive(stashId: ByteArray, exposures: Collection<Exposure>): Boolean {
        return exposures.any { exposure ->
            exposure.others.any {
                it.who.contentEquals(stashId)
            }
        }
    }

    private fun activeNominators(exposures: Collection<Exposure>): Int {
        return exposures.sumOf { it.others.size }
    }

    private fun totalStake(exposures: Collection<Exposure>): BigInteger {
        return exposures.sumOf(Exposure::total)
    }

    private fun minimumStake(exposures: Collection<Exposure>): BigInteger {
        return exposures.minOf {
            it.others.minOf(IndividualExposure::value)
        }
    }
}
