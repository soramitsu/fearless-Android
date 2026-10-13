package jp.co.soramitsu.crowdloan.api.domain

import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId

data class LegacyCrowdloanEvidence(
    val currentContributionCount: Int = 0,
    val currentContributionAmount: BigInteger = BigInteger.ZERO,
    val historicalLockCount: Int = 0,
    val historicalClaimCount: Int = 0,
    val latestHistoricalActivityAt: Long? = null
) {
    val hasLockOrClaim: Boolean
        get() = currentContributionCount > 0 ||
            currentContributionAmount > BigInteger.ZERO ||
            historicalLockCount > 0 ||
            historicalClaimCount > 0

    companion object {
        val None = LegacyCrowdloanEvidence()
    }
}

enum class LegacyCrowdloanCallKind {
    LOCK,
    CLAIM
}

interface LegacyCrowdloanRecovery {
    /**
     * Returns only account-scoped, chain-and-asset-scoped evidence. Implementations must fail
     * closed: endpoint or history failures cannot create a contextual entry.
     */
    suspend fun findEvidence(chainId: ChainId, chainAssetId: String): LegacyCrowdloanEvidence
}

fun isLegacyCrowdloanContext(
    chainId: ChainId,
    chainAssetId: String,
    utilityAssetId: String?
): Boolean = chainId in setOf(polkadotChainId, kusamaChainId) && utilityAssetId == chainAssetId

fun shouldShowLegacyCrowdloan(
    chainId: ChainId,
    chainAssetId: String,
    utilityAssetId: String?,
    evidence: LegacyCrowdloanEvidence
): Boolean = isLegacyCrowdloanContext(chainId, chainAssetId, utilityAssetId) && evidence.hasLockOrClaim

/** Only calls which prove that the selected account actually locked funds or recovered them. */
fun classifyLegacyCrowdloanCall(module: String?, call: String?): LegacyCrowdloanCallKind? {
    if (!module.equals("crowdloan", ignoreCase = true)) return null

    return when (call?.replace("_", "")?.lowercase()) {
        "contribute", "contributeall" -> LegacyCrowdloanCallKind.LOCK
        "withdraw", "refund", "claim" -> LegacyCrowdloanCallKind.CLAIM
        else -> null
    }
}
