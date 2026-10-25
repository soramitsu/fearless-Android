package jp.co.soramitsu.wallet.impl.domain.validation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NullableFeeBalanceValidationTest {
    @Test fun unknownBalanceRejectsBeforeFeeOrExtraAmountCalculations() = runBlocking {
        val validation = EnoughToPayFeesValidation<Unit, String>(
            feeExtractor = { error("Unknown balance cannot authorize a fee calculation") },
            availableBalanceProducer = { null }, errorProducer = { "unavailable" },
            extraAmountExtractor = { error("Unknown balance cannot authorize an amount calculation") }
        )
        assertEquals("unavailable", (validation.validate(Unit) as ValidationStatus.NotValid).reason)
    }

    @Test fun knownBalanceRetainsStrictFeeAndExtraAmountComparison() = runBlocking {
        val validation = EnoughToPayFeesValidation<BigDecimal, String>(
            feeExtractor = { BigDecimal.ONE }, availableBalanceProducer = { it },
            errorProducer = { "insufficient" }, extraAmountExtractor = { BigDecimal.ONE }
        )
        assertTrue(validation.validate(BigDecimal.TEN) is ValidationStatus.Valid)
        assertTrue(validation.validate(BigDecimal("2")) is ValidationStatus.NotValid)
        assertTrue(validation.validate(BigDecimal.ZERO) is ValidationStatus.NotValid)
    }

    @Test fun cancellationStillTerminatesValidation() {
        val validation = EnoughToPayFeesValidation<Unit, String>(
            feeExtractor = { BigDecimal.ONE }, availableBalanceProducer = { throw CancellationException("cancelled read") },
            errorProducer = { "must not manufacture a failure" }
        )
        val failure = assertThrows(CancellationException::class.java) { runBlocking { validation.validate(Unit) } }
        assertEquals("cancelled read", failure.message)
    }

    @Test fun actualAssetProducerPreservesMissingUnknownAndKnownBalances() = runBlocking {
        val accounts = mockk<AccountRepository> { coEvery { getSelectedMetaAccount() } returns mockk { every { id } returns 7 } }
        val repository = mockk<WalletRepository>()
        val chain = mockk<Chain> { every { ecosystem } returns Ecosystem.Ethereum; every { minSupportedVersion } returns null }
        val configuration = mockk<jp.co.soramitsu.core.models.Asset>()
        var source: Asset? = null
        coEvery { repository.getAsset(7, any(), configuration, null) } answers { source }
        val producer = EnoughToPayFeesValidation.assetBalanceProducer<Unit>(
            accounts, repository, { chain }, { "0x" + "11".repeat(20) }, { configuration }
        )
        assertNull(producer(Unit))
        source = mockk { every { availableForStaking } returns null }
        assertNull(producer(Unit))
        source = mockk { every { availableForStaking } returns BigDecimal.TEN }
        assertEquals(BigDecimal.TEN, producer(Unit))
    }
}
