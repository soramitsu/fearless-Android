package jp.co.soramitsu.crowdloan.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.api.data.repository.getContributions
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanCallKind
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanEvidence
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanRecovery
import jp.co.soramitsu.crowdloan.api.domain.classifyLegacyCrowdloanCall
import jp.co.soramitsu.crowdloan.api.domain.isLegacyCrowdloanContext
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class LegacyCrowdloanRecoveryImpl(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val crowdloanRepository: CrowdloanRepository,
    private val operationDao: OperationDao
) : LegacyCrowdloanRecovery {

    override suspend fun findEvidence(
        chainId: ChainId,
        chainAssetId: String
    ): LegacyCrowdloanEvidence {
        val chain = runCatching { chainRegistry.getChain(chainId) }.getOrNull()
            ?: return LegacyCrowdloanEvidence.None

        val utilityAssetId = chain.assets.firstOrNull { it.isUtility }?.id
        if (!isLegacyCrowdloanContext(chainId, chainAssetId, utilityAssetId)) {
            return LegacyCrowdloanEvidence.None
        }

        val account = runCatching { accountRepository.getSelectedAccount(chainId) }.getOrNull()
            ?: return LegacyCrowdloanEvidence.None

        val historicalOperations = runCatching {
            operationDao.getCompletedModuleOperations(
                address = account.address,
                chainId = chainId,
                chainAssetId = chainAssetId,
                module = "Crowdloan"
            )
        }.getOrDefault(emptyList())

        val historicalKinds = historicalOperations.mapNotNull { operation ->
            classifyLegacyCrowdloanCall(operation.module, operation.call)
        }

        val currentContributions = runCatching {
            if (!crowdloanRepository.isCrowdloansAvailable(chainId)) return@runCatching emptyList()

            val fundInfos = crowdloanRepository.allFundInfos(chainId)
            val contributionKeys = fundInfos.mapValues { (_, fundInfo) -> fundInfo.fundIndex }
            crowdloanRepository.getContributions(chainId, account.accountId, contributionKeys)
                .values
                .filterNotNull()
                .filter { it.amount > java.math.BigInteger.ZERO }
        }.getOrDefault(emptyList())

        return LegacyCrowdloanEvidence(
            currentContributionCount = currentContributions.size,
            currentContributionAmount = currentContributions.fold(java.math.BigInteger.ZERO) { total, contribution ->
                total + contribution.amount
            },
            historicalLockCount = historicalKinds.count { it == LegacyCrowdloanCallKind.LOCK },
            historicalClaimCount = historicalKinds.count { it == LegacyCrowdloanCallKind.CLAIM },
            latestHistoricalActivityAt = historicalOperations
                .filter { classifyLegacyCrowdloanCall(it.module, it.call) != null }
                .maxOfOrNull(OperationLocal::time)
        )
    }
}
