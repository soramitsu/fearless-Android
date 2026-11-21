package jp.co.soramitsu.wallet.impl.domain.validation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * Equilibrium's multi-asset balance checks, SORA's XOR-less (Bokolo Cash) transfer when XOR covers the fee, and the
 * partially known balances that the Substrate and Ethereum providers refuse.
 */
class ValidationChecksProvidersCoverageTest {

    private val equilibrium = chain(EQUILIBRIUM_ID)
    private val sora = chain(soraMainChainId)
    private val eq = equilibrium.assets.single { it.isUtility }
    private val eqd = equilibrium.assets.single { it.symbol == "eqd" }
    private val xor = sora.assets.single { it.isUtility }
    private val bokolo = sora.assets.single { it.currencyId == bokoloCashTokenId }

    private val sender = ByteArray(32) { 1 }
    private val recipient = ByteArray(32) { 2 }

    private val chains = mockk<ChainsRepository>()
    private val accounts = mockk<AccountRepository>()
    private val repository = mockk<WalletRepository>()
    private val constants = mockk<WalletConstants>()
    private val swap = mockk<PolkaswapInteractor>()
    private val minimums = mutableMapOf<CoreAsset, Result<BigInteger>>()
    private val existentialDeposit = object : ExistentialDepositUseCase {
        override suspend fun invoke(chainAsset: CoreAsset): Result<BigInteger> =
            minimums[chainAsset] ?: Result.success(BigInteger.ZERO)
    }
    private val substrate = SubstrateValidationChecksProvider(accounts, repository, chains, existentialDeposit, constants, swap)

    private var originBalances: Map<BigInteger, BigInteger>? = null
    private var destinationBalances: Map<BigInteger, BigInteger>? = null

    init {
        coEvery { chains.getChain(equilibrium.id) } returns equilibrium
        coEvery { chains.getChain(sora.id) } returns sora
        coEvery { accounts.getSelectedMetaAccount() } returns metaAccount()
        coEvery { repository.getTotalBalance(any(), any(), any()) } returns BigInteger.ZERO
        coEvery { constants.tip(any()) } returns null
        coEvery { repository.getEquilibriumAccountInfo(any(), any()) } answers {
            val balances = if (secondArg<ByteArray>().contentEquals(sender)) originBalances else destinationBalances
            balances?.let { EqAccountInfo(nonce = BigInteger.ZERO, data = EqAccountData(lock = BigInteger.ZERO, balances = it)) }
        }
        coEvery { repository.getEquilibriumAssetRates(any()) } returns mapOf(
            EQ_ID to price(units(eq, "1")),
            EQD_ID to price(units(eqd, "1"))
        )
        minimums[eq] = Result.success(units(eq, "1"))
        minimums[eqd] = Result.success(units(eqd, "1"))
    }

    @Test
    fun `an Equilibrium utility transfer that keeps both accounts alive passes every check`() = runBlocking {
        originBalances = mapOf(EQ_ID to units(eq, "10"), EQD_ID to units(eqd, "5"))
        destinationBalances = mapOf(EQ_ID to BigInteger.ZERO)

        val checks = provideEquilibrium(eq, amount = units(eq, "1"))

        assertEquals(3, checks.size)
        assertEquals(false, checks[TransferValidationResult.ExistentialDepositUnknown("")])
        assertTrue(checks.values.none { it })
    }

    @Test
    fun `an Equilibrium utility transfer larger than its balance is insufficient`() = runBlocking {
        originBalances = mapOf(EQ_ID to units(eq, "1"))

        val checks = provideEquilibrium(eq, amount = units(eq, "1"))

        assertEquals(mapOf(TransferValidationResult.InsufficientBalance to true), checks)
    }

    @Test
    fun `an Equilibrium transfer below both minimums warns and reports the recipient`() = runBlocking {
        // 1.15 EQ - 0.1 EQ sent - 0.1 EQ fee leaves 0.95 EQ, below the 1 EQ minimum; the recipient has no account.
        originBalances = mapOf(EQ_ID to units(eq, "1.15"))
        destinationBalances = null

        val checks = provideEquilibrium(eq, amount = units(eq, "0.1"))

        val warning = checks.keys.filterIsInstance<TransferValidationResult.ExistentialDepositWarning>().single()
        val deadRecipient = checks.keys.filterIsInstance<TransferValidationResult.DeadRecipient>().single()
        assertEquals(true, checks[warning])
        assertEquals(true, checks[deadRecipient])
    }

    @Test
    fun `an Equilibrium token transfer larger than the token balance is insufficient`() = runBlocking {
        originBalances = mapOf(EQ_ID to units(eq, "10"), EQD_ID to units(eqd, "0.5"))

        val checks = provideEquilibrium(eqd, amount = units(eqd, "1"))

        assertEquals(mapOf(TransferValidationResult.InsufficientBalance to true), checks)
    }

    @Test
    fun `an Equilibrium token transfer needs the utility asset for its fee`() = runBlocking {
        originBalances = mapOf(EQD_ID to units(eqd, "10"), EQ_ID to units(eq, "0.05"))

        val checks = provideEquilibrium(eqd, amount = units(eqd, "1"))

        assertEquals(mapOf(TransferValidationResult.InsufficientUtilityAssetBalance to true), checks)
    }

    @Test
    fun `an Equilibrium token transfer counts other holdings at their oracle price`() = runBlocking {
        originBalances = mapOf(EQD_ID to units(eqd, "10"), EQ_ID to units(eq, "1"), OTHER_ID to units(eq, "3"))
        destinationBalances = mapOf(EQD_ID to units(eqd, "2"))

        val checks = provideEquilibrium(eqd, amount = units(eqd, "1"))

        assertEquals(3, checks.size)
        assertTrue(checks.values.none { it })
    }

    @Test
    fun `a Bokolo Cash transfer without a known XOR balance is unavailable`() = runBlocking {
        coEvery { repository.getAsset(any(), any(), any(), any()) } returns null

        val checks = substrate.provide(units(bokolo, "1"), balance(bokolo, "10"), soraRecipient(), FEE)

        assertEquals(mapOf(TransferValidationResult.BalanceUnavailable to true), checks)
    }

    @Test
    fun `a Bokolo Cash transfer pays its fee in XOR when XOR covers it`() = runBlocking {
        coEvery { repository.getAsset(any(), any(), any(), any()) } returns balance(xor, "10")

        val checks = substrate.provide(units(bokolo, "1"), balance(bokolo, "10"), soraRecipient(), FEE)

        assertEquals(false, checks[TransferValidationResult.InsufficientBalance])
        val warning = checks.keys.filterIsInstance<TransferValidationResult.ExistentialDepositWarning>().single()
        assertFalse(checks.getValue(warning))
        coVerify(exactly = 0) { swap.calcDetails(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a Substrate send with an unknown send-available balance is unavailable`() = runBlocking {
        val partial = partialAsset(eq, transferable = units(eq, "10"), sendAvailable = null, free = units(eq, "10"))

        val checks = substrate.provide(units(eq, "1"), partial, equilibriumRecipient(), FEE)

        assertEquals(mapOf(TransferValidationResult.BalanceUnavailable to true), checks)
        coVerify(exactly = 0) { chains.getChain(any()) }
    }

    @Test
    fun `an Ethereum send with an unknown free balance is unavailable`() = runBlocking {
        val provider = EthereumValidationChecksProvider(chains, accounts, existentialDeposit, repository)
        val partial = partialAsset(eq, transferable = units(eq, "10"), sendAvailable = units(eq, "10"), free = null)

        val checks = provider.provide(units(eq, "1"), partial, "0x" + "ab".repeat(20), FEE)

        assertEquals(mapOf(TransferValidationResult.BalanceUnavailable to true), checks)
        coVerify(exactly = 0) { chains.getChain(any()) }
    }

    private suspend fun provideEquilibrium(asset: CoreAsset, amount: BigInteger) =
        substrate.provide(amount, balance(asset, "10"), equilibriumRecipient(), units(eq, "0.1"))

    private fun equilibriumRecipient() = recipient.toAddress(equilibrium.addressPrefix.toShort())

    private fun soraRecipient() = recipient.toAddress(sora.addressPrefix.toShort())

    private fun price(planks: BigInteger) = EqOraclePricePoint(
        blockNumber = BigInteger.ONE,
        timestamp = BigInteger.ONE,
        lastFinRecalcTimestamp = BigInteger.ONE,
        price = planks,
        dataPoints = emptyList()
    )

    private fun units(asset: CoreAsset, amount: String): BigInteger =
        BigDecimal(amount).movePointRight(asset.precision).toBigIntegerExact()

    private fun partialAsset(
        asset: CoreAsset,
        transferable: BigInteger?,
        sendAvailable: BigInteger?,
        free: BigInteger?
    ): Asset {
        val token = Token(configuration = asset, fiatRate = null, fiatSymbol = null, recentRateChange = null)
        return mockk {
            every { this@mockk.token } returns token
            every { transferableInPlanks } returns transferable
            every { sendAvailableInPlanks } returns sendAvailable
            every { freeInPlanks } returns free
        }
    }

    private fun balance(asset: CoreAsset, free: String) = Asset(
        metaId = 1,
        token = Token(configuration = asset, fiatRate = null, fiatSymbol = null, recentRateChange = null),
        accountId = sender,
        freeInPlanks = units(asset, free),
        reservedInPlanks = BigInteger.ZERO,
        miscFrozenInPlanks = BigInteger.ZERO,
        feeFrozenInPlanks = BigInteger.ZERO,
        bondedInPlanks = null,
        redeemableInPlanks = null,
        unbondingInPlanks = null,
        sortIndex = 0,
        enabled = true,
        minSupportedVersion = null,
        chainAccountName = null,
        markedNotNeed = false,
        status = null
    )

    private fun metaAccount() = MetaAccount(
        id = 1,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = sender,
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = sender,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Wallet",
        initialized = true
    )

    private companion object {
        const val EQUILIBRIUM_ID = "89d3ec46d2fb43ef5a9713833373d5ea666b092fa8fd68fbc34596036571b907"
        val EQ_ID: BigInteger = BigInteger.valueOf(25_969)
        val EQD_ID: BigInteger = BigInteger.valueOf(6_648_164)
        val OTHER_ID: BigInteger = BigInteger.valueOf(6_582_132)
        val FEE: BigInteger = BigInteger.TEN.pow(15)

        val registry: List<Chain> by lazy {
            val file = listOf(File("../runtime/src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
                .first(File::exists)
            val type = object : TypeToken<List<ChainRemote>>() {}.type
            Gson().fromJson<List<ChainRemote>>(file.readText(), type).map(ChainRemote::toChain)
        }

        fun chain(id: String): Chain = registry.single { it.id == id }
    }
}
