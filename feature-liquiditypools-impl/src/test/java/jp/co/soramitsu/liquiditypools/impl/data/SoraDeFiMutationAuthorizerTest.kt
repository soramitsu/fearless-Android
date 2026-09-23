package jp.co.soramitsu.liquiditypools.impl.data

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SoraDeFiMutationAuthorizerTest {

    @Test
    fun `positive contextual authorization resolves exact assets and fresh input plus XOR balances`() = runBlocking {
        val harness = Harness()

        val context = harness.authorizer.authorize(
            feature = SoraMutationFeature.Liquidity,
            chainId = soraMainChainId,
            requestedAssets = listOf(CanonicalSoraAsset(harness.inputAsset)),
            requiredCalls = setOf(DEPOSIT_CALL)
        )
        harness.authorizer.requireFreshBalances(
            context,
            listOf(SoraAssetSpend(harness.inputAsset, BigInteger.valueOf(100), "input unavailable")),
            BigInteger.TEN
        )

        assertEquals(META_ID, context.identity.metaId)
        assertEquals(harness.inputAsset, context.assets.single())
        assertEquals(harness.xorAsset, context.feeAsset)
        verify(harness.walletRepository).getAccountSpendableBalance(harness.inputAsset, ACCOUNT_ID)
        verify(harness.walletRepository).getAccountSpendableBalance(harness.xorAsset, ACCOUNT_ID)
        Unit
    }

    @Test
    fun `positive exact amount is converted with canonical precision`() {
        val asset = mock<Asset> {
            on { precision }.thenReturn(2)
        }

        assertEquals(BigInteger.valueOf(123), asset.exactPositivePlanks(BigDecimal("1.23"), "amount"))
    }

    @Test
    fun `zero and excess precision amounts fail closed`() {
        val asset = mock<Asset> {
            on { precision }.thenReturn(2)
        }

        val zero = runCatching { asset.exactPositivePlanks(BigDecimal.ZERO, "amount") }.exceptionOrNull()
        val excessPrecision = runCatching {
            asset.exactPositivePlanks(BigDecimal("1.234"), "amount")
        }.exceptionOrNull()

        assertEquals("amount must be greater than zero", zero?.message)
        assertEquals("amount exceeds the canonical asset precision", excessPrecision?.message)
    }

    @Test
    fun `disabled remote switch fails closed before resolving network context`() = runBlocking {
        val harness = Harness(liquidityEnabled = false)

        val reason = harness.authorizer.capabilityReason(
            SoraMutationFeature.Liquidity,
            soraMainChainId,
            setOf(DEPOSIT_CALL)
        )

        assertEquals("Polkaswap liquidity actions are temporarily disabled", reason)
        verify(harness.chainRegistry, never()).getChain(any())
        Unit
    }

    @Test
    fun `missing runtime call fails contextual capability closed`() = runBlocking {
        val harness = Harness(runtimeCalls = emptySet())

        val reason = harness.authorizer.capabilityReason(
            SoraMutationFeature.Liquidity,
            soraMainChainId,
            setOf(DEPOSIT_CALL)
        )

        assertTrue(reason.orEmpty().contains("PoolXYK.deposit_liquidity"))
    }

    @Test
    fun `watch only selected SORA account cannot authorize`() = runBlocking {
        val harness = Harness(hasSigningMaterial = false)

        val failure = runCatching {
            harness.authorizer.authorize(
                SoraMutationFeature.Demeter,
                soraMainChainId,
                listOf(CanonicalSoraAsset(harness.inputAsset)),
                setOf(DEPOSIT_CALL)
            )
        }.exceptionOrNull()

        assertEquals("This SORA account is watch-only or has no supported signer", failure?.message)
    }

    @Test
    fun `canonical precision mismatch cannot authorize`() = runBlocking {
        val harness = Harness()

        val failure = runCatching {
            harness.authorizer.authorize(
                SoraMutationFeature.Liquidity,
                soraMainChainId,
                listOf(CanonicalSoraAsset(harness.inputAsset).copy(precision = PRECISION - 1)),
                setOf(DEPOSIT_CALL)
            )
        }.exceptionOrNull()

        assertEquals("SORA asset identity or precision changed", failure?.message)
    }

    @Test
    fun `impostor XOR metadata cannot authorize fee balance`() = runBlocking {
        val harness = Harness(xorCurrencyId = "0x03")

        val failure = runCatching {
            harness.authorizer.authorize(
                SoraMutationFeature.Liquidity,
                soraMainChainId,
                listOf(CanonicalSoraAsset(harness.inputAsset)),
                setOf(DEPOSIT_CALL)
            )
        }.exceptionOrNull()

        assertEquals("The exact XOR fee asset is unavailable on SORA", failure?.message)
    }

    @Test
    fun `fresh XOR input balance must cover both input maximum and estimated fee`() = runBlocking {
        val harness = Harness(inputIsXor = true, spendableBalance = BigInteger.valueOf(100))
        val context = harness.authorizer.authorize(
            SoraMutationFeature.Liquidity,
            soraMainChainId,
            listOf(CanonicalSoraAsset(harness.inputAsset)),
            setOf(DEPOSIT_CALL)
        )

        val failure = runCatching {
            harness.authorizer.requireFreshBalances(
                context,
                listOf(SoraAssetSpend(harness.inputAsset, BigInteger.valueOf(95), "combined XOR unavailable")),
                BigInteger.TEN
            )
        }.exceptionOrNull()

        assertEquals("combined XOR unavailable", failure?.message)
    }

    @Test
    fun `locked input is rejected when fresh spendable balance is below raw free balance`() = runBlocking {
        val harness = Harness(spendableBalance = BigInteger.valueOf(50))
        val context = harness.authorizer.authorize(
            SoraMutationFeature.Liquidity,
            soraMainChainId,
            listOf(CanonicalSoraAsset(harness.inputAsset)),
            setOf(DEPOSIT_CALL)
        )

        val failure = runCatching {
            harness.authorizer.requireFreshBalances(
                context,
                listOf(SoraAssetSpend(harness.inputAsset, BigInteger.valueOf(60), "locked input unavailable")),
                BigInteger.ZERO
            )
        }.exceptionOrNull()

        assertEquals("locked input unavailable", failure?.message)
        verify(harness.walletRepository, never()).getAccountFreeBalance(any(), any())
        Unit
    }

    @Test
    fun `final authorization rejects a selected account identity change`() = runBlocking {
        val harness = Harness()
        val preliminary = harness.authorizer.authorize(
            SoraMutationFeature.Liquidity,
            soraMainChainId,
            listOf(CanonicalSoraAsset(harness.inputAsset)),
            setOf(DEPOSIT_CALL)
        )
        val changedMetaAccount = harness.metaAccount(metaId = META_ID + 1, accountId = ByteArray(32) { 9 })
        whenever(harness.accountRepository.getSelectedMetaAccount()).thenReturn(
            changedMetaAccount
        )

        val failure = runCatching {
            harness.authorizer.authorize(
                SoraMutationFeature.Liquidity,
                soraMainChainId,
                listOf(CanonicalSoraAsset(harness.inputAsset)),
                setOf(DEPOSIT_CALL),
                expectedIdentity = preliminary.identity
            )
        }.exceptionOrNull()

        assertEquals("The selected SORA account changed while authorizing this action", failure?.message)
    }

    private class Harness(
        liquidityEnabled: Boolean = true,
        hasSigningMaterial: Boolean = true,
        runtimeCalls: Set<SoraRuntimeCall> = setOf(DEPOSIT_CALL),
        inputIsXor: Boolean = false,
        spendableBalance: BigInteger = BigInteger.valueOf(1_000),
        xorCurrencyId: String = XOR_CURRENCY_ID
    ) {
        val chainRegistry = mock<ChainRegistry>()
        val accountRepository = mock<AccountRepository>()
        val walletRepository = mock<WalletRepository>()
        private val toggleStore = mock<ProductFeatureToggleStore>()
        val xorAsset = asset("xor", xorCurrencyId, "XOR", isUtility = true)
        val inputAsset = if (inputIsXor) xorAsset else asset("input", INPUT_CURRENCY_ID, "VAL", isUtility = false)
        private val chain = mock<Chain> {
            on { id }.thenReturn(soraMainChainId)
            on { ecosystem }.thenReturn(Ecosystem.Substrate)
            on { addressPrefix }.thenReturn(69)
            on { assets }.thenReturn(listOf(inputAsset, xorAsset).distinct())
        }
        val authorizer = SoraDeFiMutationAuthorizer(
            chainRegistry,
            accountRepository,
            walletRepository,
            toggleStore
        )

        init {
            val runtimeSnapshot = runtime(runtimeCalls)
            val selectedMetaAccount = metaAccount()
            val substrateSecrets = if (hasSigningMaterial) mock<EncodableStruct<SubstrateSecrets>>() else null
            runBlocking {
                whenever(toggleStore.polkaswapMutationsEnabled).thenReturn(liquidityEnabled)
                whenever(toggleStore.demeterMutationsEnabled).thenReturn(true)
                whenever(chainRegistry.getChain(soraMainChainId)).thenReturn(chain)
                whenever(chainRegistry.getRuntimeOrNull(soraMainChainId)).thenReturn(runtimeSnapshot)
                whenever(accountRepository.getSelectedMetaAccount()).thenReturn(selectedMetaAccount)
                whenever(accountRepository.isWalletRecoveryRequired(any())).thenReturn(false)
                whenever(accountRepository.getSubstrateSecrets(any())).thenReturn(substrateSecrets)
                whenever(walletRepository.getAccountSpendableBalance(any(), eq(ACCOUNT_ID)))
                    .thenReturn(spendableBalance)
            }
        }

        fun metaAccount(metaId: Long = META_ID, accountId: ByteArray = ACCOUNT_ID) = mock<MetaAccount> {
            on { id }.thenReturn(metaId)
            on { chainAccounts }.thenReturn(emptyMap())
            on { substrateAccountId }.thenReturn(accountId)
        }

        private fun runtime(calls: Set<SoraRuntimeCall>): RuntimeSnapshot {
            val modules = calls.groupBy(SoraRuntimeCall::pallet).mapValues { (pallet, palletCalls) ->
                Module(
                    name = pallet,
                    storage = null,
                    calls = palletCalls.associate { required ->
                        required.call to mock<MetadataFunction>()
                    },
                    events = null,
                    constants = emptyMap(),
                    errors = emptyMap(),
                    index = BigInteger.ZERO
                )
            }
            return RuntimeSnapshot(
                typeRegistry = mock<TypeRegistry>(),
                metadata = RuntimeMetadata(
                    runtimeVersion = BigInteger.ONE,
                    modules = modules,
                    extrinsic = ExtrinsicMetadata(BigInteger.ONE, emptyList())
                )
            )
        }

        private fun asset(id: String, currencyId: String, symbol: String, isUtility: Boolean) = mock<Asset> {
            on { this.id }.thenReturn(id)
            on { chainId }.thenReturn(soraMainChainId)
            on { this.currencyId }.thenReturn(currencyId)
            on { precision }.thenReturn(PRECISION)
            on { this.symbol }.thenReturn(symbol)
            on { this.isUtility }.thenReturn(isUtility)
        }
    }

    private companion object {
        val ACCOUNT_ID = ByteArray(32) { 7 }
        const val META_ID = 7L
        const val PRECISION = 18
        const val INPUT_CURRENCY_ID = "0x01"
        const val XOR_CURRENCY_ID =
            "0x0200000000000000000000000000000000000000000000000000000000000000"
        val DEPOSIT_CALL = SoraRuntimeCall("PoolXYK", "deposit_liquidity")
    }
}
