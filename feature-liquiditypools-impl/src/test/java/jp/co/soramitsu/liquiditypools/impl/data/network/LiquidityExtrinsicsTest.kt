package jp.co.soramitsu.liquiditypools.impl.data.network

import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.instances.AddressInstanceConstructor
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.PSWAP_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.data.SoraRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** Every liquidity and Demeter call builder is encoded against the SORA mainnet runtime metadata. */
class LiquidityExtrinsicsTest {

    @Test
    fun `adding to an unregistered pair registers, initializes and deposits in one batch`() {
        val extrinsic = builder().apply { addLiquidity(pairPresented = false, pairEnabled = false) }.build(useBatchAll = true)

        // TradingPair.register, PoolXYK.initialize_pool and PoolXYK.deposit_liquidity all start with dex 0, XOR, VAL.
        assertEquals(3, extrinsic.occurrences(DEX_0 + hex(XOR_ID) + hex(VAL_ID)))
    }

    @Test
    fun `adding to an enabled pair without a pool only initializes and deposits`() {
        val extrinsic = builder().apply { addLiquidity(pairPresented = false, pairEnabled = true) }.build(useBatchAll = true)

        assertEquals(2, extrinsic.occurrences(DEX_0 + hex(XOR_ID) + hex(VAL_ID)))
    }

    @Test
    fun `adding to an existing pool is a single deposit with desired and minimum amounts`() {
        val extrinsic = builder().apply { addLiquidity(pairPresented = true, pairEnabled = true) }.build()

        assertEquals(1, extrinsic.occurrences(DEX_0 + hex(XOR_ID) + hex(VAL_ID)))
        assertTrue(extrinsic.contains(SoraRuntime.u128(BigInteger.valueOf(1_000)) + SoraRuntime.u128(BigInteger.valueOf(2_000))))
        assertTrue(extrinsic.contains(SoraRuntime.u128(BigInteger.valueOf(990)) + SoraRuntime.u128(BigInteger.valueOf(1_980))))
    }

    @Test
    fun `missing asset ids add no liquidity calls`() {
        val builder = mockk<ExtrinsicBuilder>(relaxed = true)

        builder.liquidityAdd(0, null, VAL_ID, false, false, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE)
        builder.liquidityAdd(0, XOR_ID, null, true, true, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE)

        verify(exactly = 0) { builder.call(any<String>(), any(), any()) }
    }

    @Test
    fun `withdrawal encodes both outputs, the marker amount and the minimums`() {
        val extrinsic = builder().removeLiquidity(
            dexId = 0,
            outputAssetIdA = XOR_ID,
            outputAssetIdB = VAL_ID,
            markerAssetDesired = BigInteger.valueOf(500),
            outputAMin = BigInteger.valueOf(7),
            outputBMin = BigInteger.valueOf(8)
        ).build()

        val amounts = listOf(500L, 7L, 8L).joinToString("") { SoraRuntime.u128(BigInteger.valueOf(it)) }
        assertTrue(extrinsic.contains(DEX_0 + hex(XOR_ID) + hex(VAL_ID) + amounts))
    }

    @Test
    fun `Demeter deposit, withdraw and claim name the farm's base, pool and reward assets`() {
        val farm = hex(XOR_ID) + hex(VAL_ID) + hex(PSWAP_ID)
        val amount = SoraRuntime.u128(BigInteger.valueOf(42))

        val deposit = builder().demeterDeposit(XOR_ID, VAL_ID, PSWAP_ID, BigInteger.valueOf(42)).build()
        val withdraw = builder().demeterWithdraw(XOR_ID, VAL_ID, PSWAP_ID, BigInteger.valueOf(42)).build()
        val claim = builder().demeterClaimRewards(XOR_ID, VAL_ID, PSWAP_ID).build()

        // deposit(base, pool, reward, is_farm, pooled_tokens); withdraw(base, pool, reward, pooled_tokens, is_farm).
        assertTrue(deposit.contains(farm + "01" + amount))
        assertTrue(withdraw.contains(farm + amount + "01"))
        assertTrue(claim.contains(farm + "01"))
    }

    private fun ExtrinsicBuilder.addLiquidity(pairPresented: Boolean, pairEnabled: Boolean) {
        liquidityAdd(
            dexId = 0,
            baseTokenId = XOR_ID,
            targetTokenId = VAL_ID,
            pairPresented = pairPresented,
            pairEnabled = pairEnabled,
            tokenBaseAmount = BigInteger.valueOf(1_000),
            tokenTargetAmount = BigInteger.valueOf(2_000),
            amountBaseMin = BigInteger.valueOf(990),
            amountTargetMin = BigInteger.valueOf(1_980)
        )
    }

    private fun builder(): ExtrinsicBuilder {
        val runtime = SoraRuntime.runtime
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 7 })
        return ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = BigInteger.ZERO,
            runtimeVersion = RuntimeVersion(SPEC_VERSION, TRANSACTION_VERSION),
            genesisHash = ByteArray(32),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = AddressInstanceConstructor.constructInstance(runtime.typeRegistry, keypair.publicKey),
            era = Era.Immortal
        )
    }

    private fun hex(assetId: String) = assetId.removePrefix("0x")

    private fun String.occurrences(part: String) = windowed(part.length).count { it == part }

    private companion object {
        const val DEX_0 = "00000000"
        const val SPEC_VERSION = 133
        const val TRANSACTION_VERSION = 131
    }
}
