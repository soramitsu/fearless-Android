package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.multiAddressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bondExtra
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.claimPayout
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.createPoolBouncer
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.createPoolStateToggler
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.joinPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.nominatePool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setPoolMetadata
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.unbondFromPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.updateRolesBouncer
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.updateRolesStateToggler
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.withdrawUnbondedFromPool
import jp.co.soramitsu.staking.impl.presentation.common.EditPoolFlowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class StakingPoolApi(
    private val extrinsicService: ExtrinsicService,
    private val stakingSharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val joinJournal: PoolJoinJournal,
    private val finalityReader: FinalizedExtrinsicReader
) {
    private val joinReconciler = PoolJoinReconciler(joinJournal, finalityReader)

    suspend fun pendingJoin(chain: Chain, accountAddress: String, walletId: Long): PoolJoinJournal.Payment? =
        withContext(Dispatchers.IO) { joinJournal.read(joinScope(chain, accountAddress, walletId)) }

    suspend fun reconcileJoin(
        chain: Chain, accountAddress: String, walletId: Long, attemptId: String,
        beforeHeight: BigInteger?, onFinalized: () -> Unit
    ): Result<PoolJoinReconciler.Result> = withContext(Dispatchers.IO) {
        runCatching { joinReconciler.check(joinScope(chain, accountAddress, walletId), attemptId, beforeHeight, onFinalized) }
    }

    private fun joinScope(chain: Chain, accountAddress: String, walletId: Long) = PoolJoinJournal.Scope(
        walletId, chain.id.removePrefix("0x").lowercase(), chain.accountIdOf(accountAddress).toHexString(withPrefix = false)
    )
    suspend fun estimateJoinFee(
        chain: Chain,
        accountAddress: String,
        amountInPlanks: BigInteger,
        walletId: Long,
        poolId: BigInteger
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.estimateFeeForWallet(chain, accountId, walletId) {
                joinPool(amountInPlanks, poolId)
            }
        }
    }

    /** [onSubmitted] records known success synchronously on IO and must not throw or perform UI work. */
    suspend fun joinPool(
        chain: Chain,
        accountAddress: String,
        amountInPlanks: BigInteger,
        poolId: BigInteger,
        walletId: Long,
        authorize: (String) -> MutationExecutionGuard,
        onSubmitted: () -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val accountId = chain.accountIdOf(accountAddress)
            val scope = joinScope(chain, accountAddress, walletId)
            joinJournal.withScope(scope) {
                runCatching {
                    joinJournal.requireAvailable(scope)
                    val anchor = finalityReader.anchor(chain.id)
                    val tracking = PoolJoinSubmissionTracking(joinJournal, scope, amountInPlanks, poolId, anchor, onSubmitted = onSubmitted)
                    extrinsicService.submitTrackedExtrinsicForWallet(chain, accountId, walletId, authorize, tracking) {
                        joinPool(amountInPlanks, poolId)
                    }.getOrThrow()
                }
            }
        }
    }

    suspend fun estimateCreatePoolFee(
        chain: Chain,
        walletId: Long,
        name: String,
        poolId: BigInteger,
        amountInPlanks: BigInteger,
        rootAddress: String,
        nominatorAddress: String,
        stateTogglerAddress: String
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val rootMultiAddress = chain.multiAddressOf(rootAddress)
            val nominatorMultiAddress = chain.multiAddressOf(nominatorAddress)
            val stateTogglerMultiAddress = chain.multiAddressOf(stateTogglerAddress)
            val root = chain.accountIdOf(rootAddress)
            val version = chainRegistry.getRemoteRuntimeVersion(chain.id)!!

            if (version < 9390) {
                extrinsicService.estimateFeeForWallet(chain, root, walletId, useBatchAll = true) {
                    createPoolStateToggler(amountInPlanks, rootMultiAddress, nominatorMultiAddress, stateTogglerMultiAddress)
                    setPoolMetadata(poolId, name.encodeToByteArray())
                }
            } else {
                extrinsicService.estimateFeeForWallet(chain, root, walletId, useBatchAll = true) {
                    createPoolBouncer(amountInPlanks, rootMultiAddress, nominatorMultiAddress, stateTogglerMultiAddress)
                    setPoolMetadata(poolId, name.encodeToByteArray())
                }
            }
        }
    }

    /** Retains exact creation bytes before output; acknowledgement is recorded before IO dispatch-back. */
    suspend fun createPool(
        chain: Chain,
        walletId: Long,
        name: String,
        poolId: BigInteger,
        amountInPlanks: BigInteger,
        rootAddress: String,
        nominatorAddress: String,
        stateTogglerAddress: String,
        authorize: (String) -> MutationExecutionGuard,
        onSubmitted: () -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val scope = joinScope(chain, rootAddress, walletId)
        joinJournal.withScope(scope) {
            runCatching {
                joinJournal.requireAvailable(scope)
                val anchor = finalityReader.anchor(chain.id)
                val tracking = PoolJoinSubmissionTracking(joinJournal, scope, amountInPlanks, poolId, anchor,
                    PoolJoinJournal.CreateDetails(name, nominatorAddress, stateTogglerAddress), onSubmitted)
                val root = chain.accountIdOf(rootAddress)
                val rootMultiAddress = chain.multiAddressOf(rootAddress)
                val nominatorMultiAddress = chain.multiAddressOf(nominatorAddress)
                val stateTogglerMultiAddress = chain.multiAddressOf(stateTogglerAddress)
                val version = chainRegistry.getRemoteRuntimeVersion(chain.id)!!
                extrinsicService.submitTrackedExtrinsicForWallet(chain, root, walletId, authorize, tracking, useBatchAll = true) {
                    if (version < 9390) {
                        createPoolStateToggler(amountInPlanks, rootMultiAddress, nominatorMultiAddress, stateTogglerMultiAddress)
                    } else {
                        createPoolBouncer(amountInPlanks, rootMultiAddress, nominatorMultiAddress, stateTogglerMultiAddress)
                    }
                    setPoolMetadata(poolId, name.encodeToByteArray())
                }.getOrThrow()
            }
        }
    }

    suspend fun estimateNominatePoolFee(
        accountAddress: String,
        poolId: BigInteger,
        vararg validators: AccountId
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.estimateFee(chain, accountId) {
                nominatePool(poolId, validators.toList())
            }
        }
    }

    suspend fun nominatePool(
        poolId: BigInteger,
        accountAddress: String,
        vararg validators: AccountId
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                nominatePool(poolId, validators.toList())
            }
        }
    }

    suspend fun estimateClaimPayoutFee(accountAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.estimateFee(chain, accountId) {
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
            val multiAddress = chain.multiAddressOf(accountAddress)
            val accountId = chain.accountIdOf(accountAddress)
            extrinsicService.estimateFee(chain, accountId) {
                withdrawUnbondedFromPool(accountId, multiAddress)
            }
        }
    }

    suspend fun withdrawUnbonded(accountAddress: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)
            val multiAddress = chain.multiAddressOf(accountAddress)
            extrinsicService.submitExtrinsic(chain, accountId) {
                withdrawUnbondedFromPool(accountId, multiAddress)
            }
        }
    }

    suspend fun estimateUnbondFee(accountAddress: String, unbondingAmount: BigInteger): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val multiAddress = chain.multiAddressOf(accountAddress)
            val accountId = chain.accountIdOf(accountAddress)
            extrinsicService.estimateFee(chain, accountId) {
                unbondFromPool(accountId, multiAddress, unbondingAmount)
            }
        }
    }

    suspend fun unbond(accountAddress: String, unbondingAmount: BigInteger): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)
            val multiAddress = chain.multiAddressOf(accountAddress)
            extrinsicService.submitExtrinsic(chain, accountId) {
                unbondFromPool(accountId, multiAddress, unbondingAmount)
            }
        }
    }

    suspend fun estimateBondExtraFee(accountAddress: String, extraAmount: BigInteger): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.estimateFee(chain, accountId) {
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

    suspend fun estimateEditPool(state: EditPoolFlowState, address: String): BigInteger {
        return withContext(Dispatchers.IO) {
            val poolId = state.poolId
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(address)

            val version = chainRegistry.getRemoteRuntimeVersion(chain.id)!!
            if (version < 9390) {
                extrinsicService.estimateFee(chain, accountId) {
                    state.newPoolName?.let { setPoolMetadata(poolId, it.encodeToByteArray()) }
                    updateRolesStateToggler(state)
                }
            } else {
                extrinsicService.estimateFee(chain, accountId) {
                    state.newPoolName?.let { setPoolMetadata(poolId, it.encodeToByteArray()) }
                    updateRolesBouncer(state)
                }
            }
        }
    }

    suspend fun editPool(state: EditPoolFlowState, address: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val poolId = state.poolId
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(address)
            val version = chainRegistry.getRemoteRuntimeVersion(chain.id)!!

            if (version < 9390) {
                extrinsicService.submitExtrinsic(chain, accountId) {
                    state.newPoolName?.let { setPoolMetadata(poolId, it.encodeToByteArray()) }
                    updateRolesStateToggler(state)
                }
            } else {
                extrinsicService.submitExtrinsic(chain, accountId) {
                    state.newPoolName?.let { setPoolMetadata(poolId, it.encodeToByteArray()) }
                    updateRolesBouncer(state)
                }
            }
        }
    }
}

private fun ExtrinsicBuilder.updateRolesStateToggler(state: EditPoolFlowState) {
    val poolId = state.poolId
    val rootChanged = !state.initialRoot.contentEquals(state.newRoot)
    val nominatorChanged = !state.initialNominator.contentEquals(state.newNominator)
    val stateTogglerChanged = !state.initialStateToggler.contentEquals(state.newStateToggler)

    val rolesChanged = rootChanged || nominatorChanged || stateTogglerChanged
    if (rolesChanged) {
        this@updateRolesStateToggler.updateRolesStateToggler(
            poolId,
            state.newRoot.toRoleUpdateEntry(rootChanged),
            state.newNominator.toRoleUpdateEntry(nominatorChanged),
            state.newStateToggler.toRoleUpdateEntry(stateTogglerChanged)
        )
    }
}

private fun ExtrinsicBuilder.updateRolesBouncer(state: EditPoolFlowState) {
    val poolId = state.poolId
    val rootChanged = !state.initialRoot.contentEquals(state.newRoot)
    val nominatorChanged = !state.initialNominator.contentEquals(state.newNominator)
    val stateTogglerChanged = !state.initialStateToggler.contentEquals(state.newStateToggler)

    val rolesChanged = rootChanged || nominatorChanged || stateTogglerChanged
    if (rolesChanged) {
        this.updateRolesBouncer(
            poolId,
            state.newRoot.toRoleUpdateEntry(rootChanged),
            state.newNominator.toRoleUpdateEntry(nominatorChanged),
            state.newStateToggler.toRoleUpdateEntry(stateTogglerChanged)
        )
    }
}

private fun AccountId?.toRoleUpdateEntry(isChanged: Boolean) = when {
    this == null -> DictEnum.Entry("Noop", null)
    isChanged -> DictEnum.Entry("Set", this)
    else -> DictEnum.Entry("Noop", null)
}
