package jp.co.soramitsu.wallet.impl.domain.model

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.model.WalletAssetKey
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class AssetModelTest {
    private val config = CoreAsset(
        id = "dot",
        name = "Polkadot",
        symbol = "DOT",
        iconUrl = "icon",
        chainId = "polkadot",
        chainName = "Polkadot",
        chainIcon = null,
        isTestNet = false,
        priceId = "polkadot",
        precision = 2,
        staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = true,
        type = ChainAssetType.Normal,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = true
    )
    private val accountId = byteArrayOf(1, 2, 3)

    @Test
    fun emptyAssetIsUnloadedAndSortsLast() {
        val asset = Asset.createEmpty(config, metaId = 7, accountId = accountId, chainAccountName = "Ledger", minSupportedVersion = "2.0")

        assertEquals(7L, asset.metaId)
        assertEquals(Int.MAX_VALUE, asset.sortIndex)
        assertEquals("Ledger", asset.chainAccountName)
        assertEquals("2.0", asset.minSupportedVersion)
        assertFalse(asset.markedNotNeed)
        assertNull(asset.enabled)
        assertNull(asset.status)
        assertFalse(asset.isAssetFrozen)
        for (amount in listOf(asset.freeInPlanks, asset.reservedInPlanks, asset.bondedInPlanks, asset.transferableInPlanks)) {
            assertNull(amount)
        }
        assertNull(asset.total)
        assertNull(asset.transferable)
        assertNull(asset.fiatAmount)
        assertEquals(AssetAmountState.Missing, asset.freeInPlanksState)
        assertEquals("polkadot", asset.chainId)
        assertEquals("dot", asset.id)
        assertEquals(WalletAssetKey(7, "polkadot", accountId, "dot"), asset.uniqueKey)
        assertNull(asset.getAsFiatWithCurrency(BigDecimal.ONE))
    }

    @Test
    fun emptyAssetForAChainAccountUsesItsUtilityAsset() {
        val chain = mockk<Chain> {
            every { assets } returns listOf(config.copy(id = "other", isUtility = false), config)
            every { minSupportedVersion } returns "3.1"
        }
        val account = MetaAccount.ChainAccount(9, chain, byteArrayOf(9), accountId, CryptoType.SR25519, "Hardware")

        val asset = Asset.createEmpty(account)!!

        assertEquals(9L, asset.metaId)
        assertEquals("dot", asset.id)
        assertEquals("Hardware", asset.chainAccountName)
        assertEquals("3.1", asset.minSupportedVersion)
        assertArrayEquals(accountId, asset.accountId)
    }

    @Test
    fun chainAccountsWithoutAChainOrUtilityAssetHaveNoEmptyAsset() {
        val noChain = MetaAccount.ChainAccount(9, null, byteArrayOf(9), accountId, CryptoType.SR25519, "Hardware")
        val noUtility = MetaAccount.ChainAccount(
            9,
            mockk<Chain> { every { assets } returns listOf(config.copy(isUtility = false, isNative = false)) },
            byteArrayOf(9),
            accountId,
            CryptoType.SR25519,
            "Hardware"
        )

        assertNull(Asset.createEmpty(noChain))
        assertNull(Asset.createEmpty(noUtility))
    }

    @Test
    fun nullableConstructorDerivesBalancesFromKnownFields() {
        val asset = asset(free = "1000", reserved = "200", miscFrozen = "300", feeFrozen = "100", status = null)

        assertEquals(BigInteger("1000"), asset.freeInPlanks)
        assertEquals(BigDecimal("12.00"), asset.total)
        assertEquals(BigDecimal("2.00"), asset.reserved)
        assertEquals(BigDecimal("3.00"), asset.locked)
        assertEquals(BigDecimal("5.00"), asset.frozen)
        assertEquals(BigDecimal("7.00"), asset.transferable)
        assertEquals(BigDecimal("7.00"), asset.sendAvailable)
        assertEquals(BigDecimal("5.00"), asset.availableForStaking)
        assertEquals(BigInteger("700"), asset.transferableInPlanks)
        assertEquals(BigDecimal("0.50"), asset.bonded)
        assertEquals(BigDecimal("0.25"), asset.redeemable)
        assertEquals(BigDecimal("0.10"), asset.unbonding)
        assertEquals(1, asset.sortIndex)
        assertTrue(asset.enabled!!)
    }

    @Test
    fun frozenAssetsKeepTheirBalanceButCannotBeSent() {
        val asset = asset(free = "1000", reserved = null, miscFrozen = null, feeFrozen = null, status = "Frozen")

        assertTrue(asset.isAssetFrozen)
        assertTrue(asset.amounts.isFrozen)
        assertEquals(BigDecimal("10.00"), asset.transferable)
        assertEquals(BigInteger.ZERO, asset.sendAvailableInPlanks)
    }

    @Test
    fun fiatValueUsesTheTokenRateAndSymbol() {
        val usd = FiatValuationContext("usd", FiatCurrency("usd", "$", "USD", ""))
        val token = Token(
            configuration = config,
            fiatRateInput = AssetAmountState.Known(BigDecimal("2.50")),
            recentRateChangeInput = AssetAmountState.Known(BigDecimal.ONE),
            fiatContext = usd,
            quoteCurrencyId = "usd",
            quotePriceId = "polkadot"
        )
        val asset = asset(free = "400", reserved = null, miscFrozen = null, feeFrozen = null, status = null).copy(token = token)

        val formatted = asset.getAsFiatWithCurrency(BigDecimal("2"))!!

        assertTrue(formatted, formatted.startsWith("$"))
        assertTrue(formatted, formatted.contains("5"))
        assertNull(asset.getAsFiatWithCurrency(null))
        assertEquals(BigDecimal("10.0000"), asset.fiatAmount!!.setScale(4))
    }

    @Test
    fun totalBalanceNeedsTheFreeBalance() {
        assertEquals(BigInteger("15"), calculateTotalBalance(BigInteger("10"), BigInteger("5")))
        assertEquals(BigInteger("10"), calculateTotalBalance(BigInteger("10"), null))
        assertNull(calculateTotalBalance(null, BigInteger("5")))
    }

    @Test
    fun rawBalancesAreAValueType() {
        val balance = AssetBalance(
            metaId = 1,
            accountId = accountId,
            freeInPlanks = BigInteger.TEN,
            reservedInPlanks = null,
            miscFrozenInPlanks = null,
            feeFrozenInPlanks = null,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            enabled = true,
            status = "Liquid"
        )

        assertEquals(balance, balance.copy())
        assertNotEquals(balance, balance.copy(freeInPlanks = BigInteger.ONE))
        assertEquals("Liquid", balance.status)
        assertTrue(balance.toString().contains("freeInPlanks=10"))
    }

    private fun asset(free: String?, reserved: String?, miscFrozen: String?, feeFrozen: String?, status: String?) = Asset(
        metaId = 1,
        token = Token(configuration = config, fiatRate = null, fiatSymbol = null, recentRateChange = null),
        accountId = accountId,
        freeInPlanks = free?.toBigInteger(),
        reservedInPlanks = reserved?.toBigInteger(),
        miscFrozenInPlanks = miscFrozen?.toBigInteger(),
        feeFrozenInPlanks = feeFrozen?.toBigInteger(),
        bondedInPlanks = BigInteger("50"),
        redeemableInPlanks = BigInteger("25"),
        unbondingInPlanks = BigInteger("10"),
        sortIndex = 1,
        enabled = true,
        minSupportedVersion = null,
        chainAccountName = null,
        markedNotNeed = false,
        status = status
    )
}
