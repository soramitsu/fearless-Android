package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToWalletAccount
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfoState
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

class StakingInteractor(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val substrateCalls: SubstrateCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
) {

    fun observeNetworkInfoState(): Flow<NetworkInfoState> {
        return accountRepository.selectedNetworkTypeFlow()
            .transformLatest {

                emit(NetworkInfoState.Loading)

                val lockupPeriod = stakingRepository.getLockupPeriodInDays(it)

                stakingRepository.observeActiveEraIndex(it).collect { eraIndex ->
                    val exposures = stakingRepository.getElectedValidatorsExposure(eraIndex).values

                    val networkInfo = NetworkInfo(
                        lockupPeriodInDays = lockupPeriod,
                        minimumStake = minimumStake(exposures),
                        totalStake = totalStake(exposures),
                        nominatorsCount = activeNominators(exposures)
                    )

                    emit(NetworkInfoState.Loaded(networkInfo))
                }
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
        .map { mapAccountToWalletAccount(it) }
        .flatMapLatest { walletRepository.assetsFlow(it) }
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
    ) = withContext(Dispatchers.Default) {
        runCatching {
            val account = accountRepository.getAccount(originAddress)

            val extrinsic = extrinsicBuilderFactory.create(account)
                .bond(
                    controllerAddress = MultiAddress.Id(originAddress.toAccountId()),
                    amount = tokenType.planksFromAmount(amount),
                    payee = rewardDestination
                )
                .nominate(nominations)
                .build()

            substrateCalls.submitExtrinsic(extrinsic)
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
