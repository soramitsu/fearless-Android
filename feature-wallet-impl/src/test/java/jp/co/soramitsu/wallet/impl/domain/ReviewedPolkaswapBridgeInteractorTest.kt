package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.xcm.ReviewedBridgeQuote
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewedPolkaswapBridgeInteractorTest {

    @Test
    fun `a bridge transfer quoted for another wallet is not submitted`() = runBlocking {
        val executor = mockk<ReviewedPolkaswapBridgeExecutor>()
        val chainRegistry = mockk<ChainRegistry> {
            coEvery { getChain(any()) } returns mockk<Chain>(relaxed = true)
        }
        val selected = mockk<MetaAccount> { every { id } returns 2L }
        val accountInteractor = mockk<AccountInteractor> {
            coEvery { selectedMetaAccount() } returns selected
        }
        val interactor = ReviewedPolkaswapBridgeInteractor(executor, chainRegistry, accountInteractor)

        val result = interactor.submit(
            walletId = 1L,
            providerId = "liberland-bridge",
            routeId = "route",
            originChainId = "origin",
            destinationChainId = "destination",
            asset = mockk<Asset>(relaxed = true),
            recipientAddress = "recipient",
            amount = BigDecimal.ONE,
            confirmedQuote = ReviewedBridgeQuote(
                providerId = "liberland-bridge",
                routeId = "route",
                runtimeFingerprint = "runtime",
                executionFingerprint = "execution",
                originFeeInPlanks = BigInteger.ONE,
                destinationFeeInPlanks = BigInteger.ZERO,
                effectiveMinimumInPlanks = BigInteger.ONE
            )
        )

        assertEquals("cross_chain_selected_account_changed", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { executor.submit(any(), any()) }
    }
}
