package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.state.chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bondExtra
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.claimPayout
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.createPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.joinPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.nominatePool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setPoolMetadata
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.unbondFromPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.withdrawUnbondedFromPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StakingPoolApi(
    private val extrinsicService: ExtrinsicService,
    private val stakingSharedState: StakingSharedState
) {
    suspend fun estimateJoinFee(
        amountInPlanks: BigInteger,
        poolId: BigInteger
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            extrinsicService.estimateFee(chain) {
                joinPool(amountInPlanks, poolId)
            }
        }
    }

    suspend fun joinPool(
        accountAddress: String,
        amountInPlanks: BigInteger,
        poolId: BigInteger
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)
            extrinsicService.submitExtrinsic(chain, accountId) {
                joinPool(amountInPlanks, poolId)
            }
        }
    }

    suspend fun estimateCreatePoolFee(
        amountInPlanks: BigInteger,
        rootAddress: String,
        nominatorAddress: String,
        stateTogglerAddress: String
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val root = chain.accountIdOf(rootAddress)
            val nominator = chain.accountIdOf(nominatorAddress)
            val stateToggler = chain.accountIdOf(stateTogglerAddress)

            extrinsicService.estimateFee(chain) {
                createPool(amountInPlanks, root, nominator, stateToggler)
            }
        }
    }

    suspend fun createPool(
        amountInPlanks: BigInteger,
        rootAddress: String,
        nominatorAddress: String,
        stateTogglerAddress: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val root = chain.accountIdOf(rootAddress)
            val nominator = chain.accountIdOf(nominatorAddress)
            val stateToggler = chain.accountIdOf(stateTogglerAddress)

            extrinsicService.submitExtrinsic(chain, root) {
                createPool(amountInPlanks, root, nominator, stateToggler)
            }
        }
    }

    suspend fun estimateSetPoolMetadataFee(
        poolId: BigInteger,
        poolName: String
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()

            extrinsicService.estimateFee(chain) {
                setPoolMetadata(poolId, poolName.encodeToByteArray())
            }
        }
    }

    suspend fun setPoolMetadata(
        poolId: BigInteger,
        poolName: String,
        accountAddress: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                setPoolMetadata(poolId, poolName.encodeToByteArray())
            }
        }
    }

    suspend fun estimateNominatePoolFee(
        poolId: BigInteger,
        vararg validators: AccountId
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()

            extrinsicService.estimateFee(chain) {
                nominatePool(poolId, validators.toList())
            }
        }
    }

    suspend fun nominatePool(
        poolId: BigInteger,
        vararg validators: AccountId,
        accountAddress: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                nominatePool(poolId, validators.toList())
            }
        }
    }

    suspend fun estimateClaimPayoutFee(): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()

            extrinsicService.estimateFee(chain) {
                claimPayout()
            }
        }
    }

    suspend fun claimPayout(accountAddress: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                claimPayout()
            }
        }
    }

    suspend fun estimateWithdrawUnbondedFee(accountAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)
            extrinsicService.estimateFee(chain) {
                withdrawUnbondedFromPool(accountId)
            }
        }
    }

    suspend fun withdrawUnbonded(accountAddress: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                withdrawUnbondedFromPool(accountId)
            }
        }
    }

    suspend fun estimateUnbondFee(accountAddress: String, unbondingAmount: BigInteger): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)
            extrinsicService.estimateFee(chain) {
                unbondFromPool(accountId, unbondingAmount)
            }
        }
    }

    suspend fun unbond(accountAddress: String, unbondingAmount: BigInteger): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                unbondFromPool(accountId, unbondingAmount)
            }
        }
    }

    suspend fun estimateBondExtraFee(extraAmount: BigInteger): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            extrinsicService.estimateFee(chain) {
                bondExtra(extraAmount)
            }
        }
    }

    suspend fun bondExtra(accountAddress: String, extraAmount: BigInteger): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                bondExtra(extraAmount)
            }
        }
    }
}
