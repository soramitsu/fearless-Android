package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigInteger
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.calls.liquidityProxyQuote
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.model.Token

// Attention! Works only for the sora main net
class SoraStakingRewardsScenario(
    private val rpcCalls: RpcCalls,
    private val chainRegistry: ChainRegistry,
    private val tokenRepository: TokenRepository
) {
    companion object {
        private const val SORA_MAIN_NET_CHAIN_ID =
            "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        private const val DESIRED = "WithDesiredInput"
        private const val FILTER = "Disabled"
        private const val DEX_ID = 0
        private const val SORA_PRECISION = 18

        // XOR
        const val STAKING_ASSET_CURRENCY_ID =
            "0x0200000000000000000000000000000000000000000000000000000000000000"

        // VAL
        const val REWARD_ASSET_CURRENCY_ID =
            "0x0200040000000000000000000000000000000000000000000000000000000000"

        // VAL
        const val REWARD_ASSET_ID = "24d0809e-0a4c-42ea-bdd8-dc7a518f389c"
    }

    suspend fun getRewardAsset(): Token {
        val chain = chainRegistry.getChain(SORA_MAIN_NET_CHAIN_ID)
        val rewardAsset = requireNotNull(chain.assetsById[REWARD_ASSET_ID])
        return tokenRepository.getToken(rewardAsset)
    }

    suspend fun mainAssetToRewardAssetRate(): BigInteger {
        val amount = BigInteger.ONE
        val amountInPlanks = amount.multiply(BigInteger.TEN.pow(SORA_PRECISION))

        val quote = requireNotNull(rpcCalls.liquidityProxyQuote(
            SORA_MAIN_NET_CHAIN_ID,
            STAKING_ASSET_CURRENCY_ID,
            REWARD_ASSET_CURRENCY_ID,
            amountInPlanks,
            DESIRED,
            emptyList(),
            FILTER,
            DEX_ID
        )) { "SORA conversion quote is unavailable" }
        val rate = requireNotNull(quote.amount) { "SORA conversion quote amount is unavailable" }
        require(rate.signum() > 0) { "SORA conversion quote must be positive" }
        return rate
    }
}
