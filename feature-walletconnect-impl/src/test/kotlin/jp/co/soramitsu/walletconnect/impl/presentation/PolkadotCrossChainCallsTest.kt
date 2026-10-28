package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.walletconnect.impl.presentation.ParityRuntimes.cases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.web3j.crypto.Keys
import java.math.BigInteger

/**
 * Cross-chain calls read from live Polkadot 2005000 and Statemint 2005000 metadata (the runtime-utils parity
 * fixtures) and, for pallets those runtimes lack (xTokens, SORA and Liberland bridges), from decoded values shaped
 * as their runtimes decode them.
 */
class PolkadotCrossChainCallsTest {
    private val polkadotRuntime = ParityRuntimes.runtime("polkadot")
    private val statemintRuntime = ParityRuntimes.runtime("statemint")

    private val polkadot = testChain(POLKADOT_GENESIS, Ecosystem.Substrate).copy(name = "Polkadot", assets = listOf(asset("DOT", 10)))
    private val assetHub = testChain(STATEMINT_GENESIS, Ecosystem.Substrate)
        .copy(name = "Polkadot Asset Hub", parentId = POLKADOT_GENESIS, paraId = "1000")
    private val acala = testChain(ACALA_GENESIS, Ecosystem.Substrate).copy(name = "Acala", parentId = POLKADOT_GENESIS, paraId = "2000")
    private val catalog = listOf(polkadot, assetHub, acala)

    @Test
    fun `a relay teleport to Asset Hub shows destination beneficiary and amount and stays signable`() {
        val call = single(polkadot, polkadotCall(TELEPORT_TO_ASSET_HUB))

        assertEquals("XcmPallet.limited_teleport_assets", call.name)
        assertEquals("Polkadot Asset Hub", call.destination?.chain?.name)
        assertEquals(SS58Encoder.encode(ByteArray(32) { 0xab.toByte() }, 0), call.beneficiary)
        assertEquals(BigInteger.TEN.pow(13), call.amounts.single().planks)
        assertEquals("DOT", call.amounts.single().asset?.symbol)
        assertFalse(call.isHighRisk)
        assertNull(call.retirement)
    }

    @Test
    fun `relay reserve transfers of the relay token are refused`() {
        val reserve = single(polkadot, polkadotCall(RESERVE_TRANSFER_TO_ACALA))

        assertEquals("Acala", reserve.destination?.chain?.name)
        assertEquals(CrossChainRetirement.RelayReserve, reserve.retirement)
        assertEquals(CrossChainRetirement.RelayReserve, single(polkadot, polkadotCall(TRANSFER_ASSETS_TO_ACALA)).retirement)
        // transfer_assets to Asset Hub is a teleport, which still works.
        assertNull(single(polkadot, polkadotCall(TRANSFER_ASSETS_TO_ASSET_HUB)).retirement)
    }

    @Test
    fun `XCM to Moonbeam is refused from the relay chain and from a parachain`() {
        val send = single(polkadot, polkadotCall(SEND_TO_MOONBEAM))
        assertTrue(send.isHighRisk)
        assertEquals("Moonbeam", (send.retirement as CrossChainRetirement.Destination).destination)
        assertEquals(BigInteger.valueOf(2004), send.destination?.paraId)

        val fromAssetHub = single(assetHub, PolkadotTransaction.decodeCall(ASSET_HUB_RESERVE_TO_MOONBEAM.fromHex(), statemintRuntime)!!)
        assertEquals("Moonbeam", (fromAssetHub.retirement as CrossChainRetirement.Destination).destination)
        assertEquals(KEY_20, fromAssetHub.beneficiary)
    }

    @Test
    fun `cross-chain calls inside batches and proxies are found`() {
        val batch = PolkadotCrossChainCalls(polkadot, catalog).read(polkadotCall("1a02" + "08" + TELEPORT_TO_ASSET_HUB + SEND_TO_MOONBEAM))
        assertEquals(listOf("XcmPallet.limited_teleport_assets", "XcmPallet.send"), batch.map { it.name })
        assertTrue(batch.last().retirement is CrossChainRetirement.Destination)

        val proxied = PolkadotCrossChainCalls(polkadot, catalog).read(polkadotCall("1d00" + "00" + "11".repeat(32) + "00" + SEND_TO_MOONBEAM))
        assertTrue(proxied.single().retirement is CrossChainRetirement.Destination)
        assertTrue(PolkadotCrossChainCalls(polkadot).read(polkadotCall(BALANCES_TRANSFER)).isEmpty())
    }

    @Test
    fun `an executed XCM program is high risk and its destinations are checked`() {
        val module = polkadotRuntime.metadata.module("XcmPallet")
        val program = DictEnum.Entry(
            "V5",
            listOf(
                DictEnum.Entry("WithdrawAsset", listOf(fungible(location(0), BigInteger.TEN))),
                DictEnum.Entry(
                    "InitiateTeleport",
                    Struct.Instance(
                        mapOf(
                            "assets" to DictEnum.Entry("Wild", DictEnum.Entry("AllCounted", BigInteger.ONE)),
                            "dest" to location(0, parachain(2012)),
                            "xcm" to listOf(DictEnum.Entry("DepositAsset", Struct.Instance(mapOf("beneficiary" to location(0, accountKey20())))))
                        )
                    )
                )
            )
        )
        val execute = GenericCall.Instance(module, module.call("execute"), mapOf("message" to program, "max_weight" to null))

        val call = single(polkadot, execute)

        assertTrue(call.isHighRisk)
        assertEquals("Parallel", (call.retirement as CrossChainRetirement.Destination).destination)
        assertEquals(KEY_20, call.beneficiary)
        assertEquals(BigInteger.TEN, call.amounts.single().planks)
    }

    @Test
    fun `xTokens transfers name the beneficiary in the destination and are refused to Moonriver`() {
        val karura = testChain(KARURA_GENESIS, Ecosystem.Substrate).copy(parentId = KUSAMA_GENESIS, paraId = "2000")
        val transfer = synthetic(
            "XTokens",
            "transfer",
            "currency_id" to DictEnum.Entry("Token", DictEnum.Entry("KSM", null)),
            "amount" to BigInteger.valueOf(5),
            "dest" to DictEnum.Entry("V3", location(1, DictEnum.Entry("Parachain", BigInteger.valueOf(2023)), accountKey20(v3 = true), v3 = true)),
            "dest_weight_limit" to DictEnum.Entry("Unlimited", null)
        )

        val call = single(karura, transfer)

        assertEquals("Moonriver", (call.retirement as CrossChainRetirement.Destination).destination)
        assertEquals(KEY_20, call.beneficiary)
        assertEquals("Token(KSM)", call.amounts.single().description)
    }

    @Test
    fun `SORA bridge burns toward halted networks are refused and others are only flagged`() {
        val sora = testChain(SORA_GENESIS, Ecosystem.Substrate, addressPrefix = 69)
        fun burn(network: Any) = synthetic(
            "BridgeProxy",
            "burn",
            "network_id" to network,
            "asset_id" to Struct.Instance(mapOf("code" to List(32) { BigInteger.valueOf(2) })),
            "recipient" to DictEnum.Entry("EVM", "cd".repeat(20).fromHex()),
            "amount" to BigInteger.ONE
        )

        for (halted in listOf("Polkadot", "Kusama", "Rococo", "PolkadotAcala", "PolkadotAstar", "Liberland")) {
            val call = single(sora, burn(DictEnum.Entry("Sub", DictEnum.Entry(halted, null))))
            assertEquals(halted, (call.retirement as CrossChainRetirement.Destination).destination)
        }
        val toEthereum = single(sora, burn(DictEnum.Entry("EVM", BigInteger.ONE)))
        assertNull(toEthereum.retirement)
        assertEquals(KEY_20, toEthereum.beneficiary)

        val legacy = single(
            sora,
            synthetic(
                "EthBridge",
                "transfer_to_sidechain",
                "asset_id" to List(32) { BigInteger.valueOf(2) },
                "to" to "cd".repeat(20).fromHex(),
                "amount" to BigInteger.ONE,
                "network_id" to BigInteger.ZERO
            )
        )
        assertNull(legacy.retirement)
    }

    @Test
    fun `Liberland to SORA burns are flagged and still signable`() {
        val liberland = testChain(LIBERLAND_GENESIS, Ecosystem.Substrate, addressPrefix = 42)
        val call = single(
            liberland,
            synthetic(
                "SoraBridgeApp",
                "burn",
                "network_id" to DictEnum.Entry("Mainnet", null),
                "asset_id" to DictEnum.Entry("LLD", null),
                "recipient" to DictEnum.Entry("Sora", ByteArray(32) { 7 }),
                "amount" to BigInteger.TEN
            )
        )

        assertEquals("SORA", call.destination?.text)
        assertEquals(SS58Encoder.encode(ByteArray(32) { 7 }, 42), call.beneficiary)
        assertNull(call.retirement)
    }

    @Test
    fun `a transaction toward a retired destination is refused before it is encoded`() {
        try {
            PolkadotTransaction.read(polkadot, payloadFor("1a02" + "08" + TELEPORT_TO_ASSET_HUB + SEND_TO_MOONBEAM), polkadotRuntime)
            fail("XCM to Moonbeam must be refused")
        } catch (refused: PolkadotTransactionRefusedException) {
            assertEquals(PolkadotRefusal.RetiredDestination, refused.refusal)
        }
        val teleport = PolkadotTransaction.read(polkadot, payloadFor(TELEPORT_TO_ASSET_HUB), polkadotRuntime)
        assertEquals("XcmPallet.limited_teleport_assets", teleport.crossChainCalls.single().name)
    }

    @Test
    fun `calls nested deeper than the wallet checks are refused`() {
        var call = BALANCES_TRANSFER
        repeat(17) { call = "1a02" + "04" + call }

        try {
            PolkadotCrossChainCalls(polkadot).read(polkadotCall(call))
            fail("too deep")
        } catch (refused: PolkadotTransactionRefusedException) {
            assertEquals(PolkadotRefusal.UndecodableCall, refused.refusal)
        }
    }

    private fun single(
        origin: jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain,
        call: GenericCall.Instance
    ): CrossChainCall = PolkadotCrossChainCalls(origin, catalog).read(call).single()

    private fun polkadotCall(hex: String): GenericCall.Instance =
        requireNotNull(PolkadotTransaction.decodeCall(hex.fromHex(), polkadotRuntime))

    private fun payloadFor(methodHex: String): PolkadotSignPayload {
        val chain = ParityRuntimes.chain("polkadot")
        val case = chain.cases()[1]
        return PolkadotSignPayload.parse(ParityRuntimes.signerPayload(chain, case, methodHex.fromHex(), "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5").toString())
    }

    private fun synthetic(
        pallet: String,
        function: String,
        vararg arguments: Pair<String, Any?>
    ): GenericCall.Instance {
        val functionArguments = arguments.map { FunctionArgument(it.first, null) }
        val metadataFunction = MetadataFunction(function, functionArguments, emptyList(), 0 to 0)
        val module = Module(pallet, null, mapOf(function to metadataFunction), emptyMap(), emptyMap(), emptyMap(), BigInteger.ZERO)
        return GenericCall.Instance(module, metadataFunction, arguments.toMap())
    }

    private fun location(
        parents: Int,
        vararg junctions: DictEnum.Entry<*>,
        v3: Boolean = false
    ): Struct.Instance {
        val interior = when {
            junctions.isEmpty() -> DictEnum.Entry("Here", null)
            v3 && junctions.size == 1 -> DictEnum.Entry("X1", junctions.single())
            else -> DictEnum.Entry("X${junctions.size}", junctions.toList())
        }
        return Struct.Instance(mapOf("parents" to parents.toBigInteger(), "interior" to interior))
    }

    private fun parachain(id: Int) = DictEnum.Entry("Parachain", BigInteger.valueOf(id.toLong()))

    private fun accountKey20(v3: Boolean = false): DictEnum.Entry<Struct.Instance> {
        val key: Any = if (v3) "cd".repeat(20).fromHex() else List(20) { BigInteger.valueOf(0xcd) }
        return DictEnum.Entry("AccountKey20", Struct.Instance(mapOf("network" to null, "key" to key)))
    }

    private fun fungible(id: Struct.Instance, amount: BigInteger) =
        Struct.Instance(mapOf("id" to id, "fun" to DictEnum.Entry("Fungible", amount)))

    private fun asset(symbol: String, precision: Int) = Asset(
        id = symbol.lowercase(),
        name = symbol,
        symbol = symbol,
        iconUrl = "",
        chainId = POLKADOT_GENESIS,
        chainName = "Polkadot",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = precision,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = true,
        type = null,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = true
    )

    private companion object {
        const val STATEMINT_GENESIS = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val ACALA_GENESIS = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"
        const val KARURA_GENESIS = "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b"
        const val SORA_GENESIS = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val LIBERLAND_GENESIS = "6bd89e052d67a45bb60a9a23e8581053d5e0d619f15cb9865946937e690c42d6"

        // XcmPallet (99) calls under Polkadot 2005000 with V5 locations: 1,000 DOT to AccountId32 0xabab….
        const val ASSETS_THOUSAND_DOT = "0504000000" + "0b00a0724e1809"
        val BENEFICIARY = "0500010100" + "ab".repeat(32)
        val TELEPORT_TO_ASSET_HUB = "6309" + "05000100a10f" + BENEFICIARY + ASSETS_THOUSAND_DOT + "00000000" + "00"
        val RESERVE_TRANSFER_TO_ACALA = "6308" + "05000100411f" + BENEFICIARY + ASSETS_THOUSAND_DOT + "00000000" + "00"
        val TRANSFER_ASSETS_TO_ACALA = "630b" + "05000100411f" + BENEFICIARY + ASSETS_THOUSAND_DOT + "00000000" + "00"
        val TRANSFER_ASSETS_TO_ASSET_HUB = "630b" + "05000100a10f" + BENEFICIARY + ASSETS_THOUSAND_DOT + "00000000" + "00"

        // XcmPallet.send of ClearOrigin to Parachain(2004).
        const val SEND_TO_MOONBEAM = "6300" + "05000100511f" + "05040a"

        // Statemint PolkadotXcm (31) limited_reserve_transfer_assets with V3 locations: Parachain(2004), AccountKey20.
        val ASSET_HUB_RESERVE_TO_MOONBEAM = "1f08" + "03010100511f" + "030001" + "03" + "00" + "cd".repeat(20) +
            "03040001000004" + "00000000" + "00"

        val KEY_20: String = Keys.toChecksumAddress("0x" + "cd".repeat(20))

        // Balances.transfer_keep_alive of 1 DOT, from the parity vectors.
        val BALANCES_TRANSFER = "050300" + "11".repeat(32) + "0700e40b5402"
    }
}
