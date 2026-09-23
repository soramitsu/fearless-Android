package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeFilesCache
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.CrossChainTransfer
import jp.co.soramitsu.xcm.XcmService
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.math.BigInteger

class XcmInteractorMutationGuardTest {

    @Test
    fun `SORA KSM XCM submits exact amount plus destination fee without rounding`() {
        val asset = mock(Asset::class.java)
        `when`(asset.precision).thenReturn(18)
        `when`(asset.currencyId).thenReturn("0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8")
        val transfer = CrossChainTransfer(
            originChainId = soraMainChainId,
            destinationChainId = "destination",
            recipient = "recipient",
            amount = BigDecimal("1.234567890123"),
            destinationFee = BigDecimal("0.000000000001"),
            chainAsset = asset
        )

        assertEquals(BigInteger("1234567890124000000"), exactXcmAmountInPlanks(transfer))
        assertEquals(transfer.fullAmountInPlanks, exactXcmAmountInPlanks(transfer))
    }

    @Test
    fun `XCM rejects unsupported KSM decimals and general asset precision overflow`() {
        val ksm = mock(Asset::class.java)
        `when`(ksm.precision).thenReturn(18)
        `when`(ksm.currencyId).thenReturn("0x00117b0fa73c4672e03a7d9d774e3b3f91beb893e93d9a8d0430295f44225db8")
        fun transfer(amount: String, fee: String, asset: Asset, origin: String = soraMainChainId) = CrossChainTransfer(
            originChainId = origin,
            destinationChainId = "destination",
            recipient = "recipient",
            amount = BigDecimal(amount),
            destinationFee = BigDecimal(fee),
            chainAsset = asset
        )

        assertThrows(IllegalArgumentException::class.java) {
            exactXcmAmountInPlanks(transfer("1.0000000000001", "0", ksm))
        }
        assertThrows(IllegalArgumentException::class.java) {
            exactXcmAmountInPlanks(transfer("1", "0.0000000000001", ksm))
        }
        val other = mock(Asset::class.java)
        `when`(other.precision).thenReturn(18)
        assertThrows(ArithmeticException::class.java) {
            exactXcmAmountInPlanks(transfer("1.0000000000000000001", "0", other, "origin"))
        }
    }

    @Test
    fun `preparing reviewed quotes and crypto metadata does not read wallet secrets`() = runBlocking<Unit> {
        val accountInteractor = mock(AccountInteractor::class.java)
        val metaAccount = mock(MetaAccount::class.java)
        `when`(metaAccount.id).thenReturn(7L)
        `when`(metaAccount.substrateCryptoType).thenReturn(CryptoType.SR25519)
        `when`(accountInteractor.selectedMetaAccount()).thenReturn(metaAccount)
        val chain = mock(Chain::class.java)
        val registry = mock(ChainRegistry::class.java)
        `when`(registry.getChain("origin")).thenReturn(chain)
        val service = mock(XcmService::class.java)
        val interactor = XcmInteractor(
            mock(WalletInteractor::class.java), registry, mock(CurrentAccountAddressUseCase::class.java),
            mock(XcmEntitiesFetcher::class.java), accountInteractor, mock(RuntimeFilesCache::class.java),
            service, mock(ProductFeatureToggleStore::class.java)
        )

        interactor.prepareDataForChains("origin", "destination")

        val captured = argumentCaptor<Any>()
        verify(service).updateKeypairProvider(eq("origin"), captured.capture())
        val provider = captured.firstValue as KeypairProvider
        assertEquals(CryptoType.SR25519, provider.getCryptoTypeFor(chain, ByteArray(32)))
        verify(accountInteractor, never()).getSubstrateSecrets(7L)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { provider.getKeypairFor(chain, ByteArray(32)) }
        }
        verify(accountInteractor, never()).getSubstrateSecrets(7L)
    }

    @Test
    fun `disabled mutation switch cannot be bypassed by calling the final transfer method`() = runBlocking {
        val chainRegistry = mock(ChainRegistry::class.java)
        val currentAccountAddress = mock(CurrentAccountAddressUseCase::class.java)
        val xcmService = mock(XcmService::class.java)
        val featureToggleStore = mock(ProductFeatureToggleStore::class.java)
        `when`(featureToggleStore.xcmMutationsEnabled).thenReturn(false)

        val interactor = XcmInteractor(
            walletInteractor = mock(WalletInteractor::class.java),
            chainRegistry = chainRegistry,
            currentAccountAddress = currentAccountAddress,
            xcmEntitiesFetcher = mock(XcmEntitiesFetcher::class.java),
            accountInteractor = mock(AccountInteractor::class.java),
            runtimeFilesCache = mock(RuntimeFilesCache::class.java),
            xcmService = xcmService,
            featureToggleStore = featureToggleStore
        )

        val result = interactor.performCrossChainTransfer(mock(CrossChainTransfer::class.java))

        assertTrue(result.isFailure)
        assertEquals(
            "Cross-chain transfers are temporarily disabled.",
            result.exceptionOrNull()?.message
        )
        verifyNoInteractions(chainRegistry, currentAccountAddress, xcmService)
    }
}
