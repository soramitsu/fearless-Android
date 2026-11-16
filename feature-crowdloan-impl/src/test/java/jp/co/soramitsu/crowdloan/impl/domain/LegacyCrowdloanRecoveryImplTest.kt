package jp.co.soramitsu.crowdloan.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanEvidence
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class LegacyCrowdloanRecoveryImplTest {
    private val accountRepository = mockk<AccountRepository>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val crowdloanRepository = mockk<CrowdloanRepository>()
    private val operationDao = mockk<OperationDao>()
    private val recovery = LegacyCrowdloanRecoveryImpl(accountRepository, chainRegistry, crowdloanRepository, operationDao)

    private val account = Account("15address", "wallet", "0x" + "01".repeat(32), CryptoType.SR25519, 0)
    private val chain = mockk<Chain> {
        every { assets } returns listOf(asset("usdt", utility = false), asset("dot", utility = true))
    }

    private fun asset(id: String, utility: Boolean): CoreAsset = mockk {
        every { this@mockk.id } returns id
        every { isUtility } returns utility
    }

    private fun operation(call: String?, time: Long, module: String? = "Crowdloan") = OperationLocal(
        id = "op-$time", address = account.address, chainId = polkadotChainId, chainAssetId = "dot", time = time,
        status = OperationLocal.Status.COMPLETED, source = OperationLocal.Source.BLOCKCHAIN,
        operationType = OperationLocal.Type.EXTRINSIC, module = module, call = call
    )

    private fun fund(index: Long, paraId: Long) = FundInfo(
        ByteArray(32), BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
        null, BigInteger.valueOf(index), BigInteger.valueOf(paraId), ByteArray(32)
    )

    @Before fun setUp() {
        coEvery { chainRegistry.getChain(polkadotChainId) } returns chain
        coEvery { accountRepository.getSelectedAccount(polkadotChainId) } returns account
        coEvery { operationDao.getCompletedModuleOperations(account.address, polkadotChainId, "dot", "Crowdloan", any()) } returns listOf(
            operation("contribute", 100), operation("contribute_all", 300), operation("withdraw", 200), operation("add_memo", 500)
        )
        coEvery { crowdloanRepository.isCrowdloansAvailable(polkadotChainId) } returns true
        coEvery { crowdloanRepository.allFundInfos(polkadotChainId) } returns mapOf(
            BigInteger.valueOf(2000) to fund(1, 2000), BigInteger.valueOf(2004) to fund(2, 2004), BigInteger.valueOf(2006) to fund(3, 2006)
        )
        coEvery { crowdloanRepository.getContribution(polkadotChainId, account.accountId, any(), any()) } answers {
            when (arg<BigInteger>(3).toInt()) {
                1 -> Contribution(BigInteger.TEN, "")
                2 -> Contribution(BigInteger.ZERO, "")
                else -> null
            }
        }
    }

    @Test fun liveContributionsAndCompletedCallsBecomeEvidence() = runTest {
        val evidence = recovery.findEvidence(polkadotChainId, "dot")

        assertEquals(
            LegacyCrowdloanEvidence(
                currentContributionCount = 1,
                currentContributionAmount = BigInteger.TEN,
                historicalLockCount = 2,
                historicalClaimCount = 1,
                latestHistoricalActivityAt = 300
            ),
            evidence
        )
    }

    @Test fun relayWithoutTheCrowdloanPalletOnlyReportsHistory() = runTest {
        coEvery { crowdloanRepository.isCrowdloansAvailable(polkadotChainId) } returns false

        val evidence = recovery.findEvidence(polkadotChainId, "dot")

        assertEquals(0, evidence.currentContributionCount)
        assertEquals(2, evidence.historicalLockCount)
        coVerify(exactly = 0) { crowdloanRepository.allFundInfos(any()) }
    }

    @Test fun unreadableHistoryAndStateFailClosed() = runTest {
        coEvery { operationDao.getCompletedModuleOperations(any(), any(), any(), any(), any()) } throws IllegalStateException("db")
        coEvery { crowdloanRepository.isCrowdloansAvailable(polkadotChainId) } throws IllegalStateException("node")

        assertEquals(LegacyCrowdloanEvidence.None, recovery.findEvidence(polkadotChainId, "dot"))
    }

    @Test fun outsideTheRelayUtilityContextNothingIsLookedUp() = runTest {
        assertEquals(LegacyCrowdloanEvidence.None, recovery.findEvidence(polkadotChainId, "usdt"))
        coVerify(exactly = 0) { accountRepository.getSelectedAccount(any()) }

        coEvery { chainRegistry.getChain("westend") } returns chain
        assertEquals(LegacyCrowdloanEvidence.None, recovery.findEvidence("westend", "dot"))
    }

    @Test fun unknownChainOrAccountMeansNoEvidence() = runTest {
        coEvery { chainRegistry.getChain("missing") } throws NoSuchElementException("missing")
        assertEquals(LegacyCrowdloanEvidence.None, recovery.findEvidence("missing", "dot"))

        coEvery { accountRepository.getSelectedAccount(polkadotChainId) } throws IllegalStateException("no account")
        assertEquals(LegacyCrowdloanEvidence.None, recovery.findEvidence(polkadotChainId, "dot"))
        coVerify(exactly = 0) { operationDao.getCompletedModuleOperations(any(), any(), any(), any(), any()) }
    }
}
