package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.storeTlb
import java.math.BigInteger
import java.util.Base64
import jp.co.soramitsu.core.models.Asset as CoreAsset

/** Jetton, NFT, comment and unknown-contract messages as the approval screen describes them. */
class TonSignRequestSummariesPayloadTest {
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
        every {
            getString(any(), *anyVararg())
        } answers { "string:${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}" }
    }
    private val listedJetton = CoreAsset(
        id = USDT_MASTER.toString(userFriendly = false),
        name = "Tether",
        symbol = "USDT",
        iconUrl = "",
        chainId = "ton",
        chainName = "TON",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 6,
        staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = false,
        type = ChainAssetType.Jetton,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = false
    )
    private val chain = mockk<Chain> {
        every { assets } returns listOf(listedJetton)
        every { isTestNet } returns false
    }
    private val remote = mockk<TonRemoteSource>()
    private val summaries = TonSignRequestSummaries(resources, remote)

    @Test
    fun jettonTransfersUseTheChainsListingOrTheNetworksNames(): Unit = runBlocking {
        coEvery { remote.loadJettonBalances(chain, any()) } returns JettonsBalances(
            listOf(
                balance(USDT_WALLET, USDT_MASTER, "USD₮", 6),
                balance(OTHER_WALLET, OTHER_MASTER, "NOT", 9),
                JettonBalance("1", AccountAddress("not an address", false, true), preview(OTHER_MASTER, "BAD", 2))
            )
        )

        val usdt = summaries.summary(chain, request(jettonMessage(USDT_WALLET, BigInteger("1234567"))), PUBLIC_KEY)
        val other = summaries.summary(chain, request(jettonMessage(OTHER_WALLET, BigInteger("2000000000"))), PUBLIC_KEY)

        val usdtRows = usdt.sections.single().rows.associate { it.title to it.value }
        assertEquals("USDT", usdtRows["string:${R.string.signing_field_token}"])
        assertEquals("1.234567 USDT", usdtRows["string:${R.string.common_amount}"])
        assertEquals(TonSignRequestSummaries.ownerAddress(RECIPIENT), usdtRows["string:${R.string.signing_field_recipient}"])
        assertEquals("memo", usdtRows["string:${R.string.signing_field_comment}"])
        assertTrue(usdt.headline.endsWith("1.234567 USDT"))
        assertFalse(usdt.showsRawData)
        assertEquals("2 NOT", other.sections.single().rows.single { it.title == "string:${R.string.common_amount}" }.value)
    }

    @Test
    fun aFailedJettonLookupShowsRawUnits(): Unit = runBlocking {
        coEvery { remote.loadJettonBalances(chain, any()) } throws IllegalStateException("indexer down")

        val result = summaries.summary(chain, request(jettonMessage(USDT_WALLET, BigInteger("1234567"))), PUBLIC_KEY)

        val rows = result.sections.single().rows.associate { it.title to it.value }
        assertEquals("string:${R.string.signing_value_unknown_token}", rows["string:${R.string.signing_field_token}"])
        assertEquals("string:${R.string.signing_value_raw_units}:1234567", rows["string:${R.string.common_amount}"])
    }

    @Test
    fun nftTransfersNameTheNewOwnerAndForwarding(): Unit = runBlocking {
        val body = buildCell {
            storeUInt(0x5fcc3d14, 32)
            storeUInt(0, 64)
            storeTlb(MsgAddress, RECIPIENT)
            storeTlb(MsgAddress, RESPONSE)
            storeBit(false)
            storeTlb(Coins, Coins.ofNano(5))
            storeBit(true)
            storeRef(comment("gift"))
        }

        val result = summaries.summary(chain, request(message(NFT_ITEM, body)), PUBLIC_KEY)

        val rows = result.sections.single().rows.associate { it.title to it.value }
        assertEquals("string:${R.string.signing_action_nft_transfer}", result.headline)
        assertEquals("string:${R.string.signing_action_nft_transfer}", rows["string:${R.string.signing_field_action}"])
        assertEquals(TonSignRequestSummaries.ownerAddress(RECIPIENT), rows["string:${R.string.signing_field_recipient}"])
        assertEquals(TonSignRequestSummaries.ownerAddress(RESPONSE), rows["string:${R.string.signing_field_excess_to}"])
        assertEquals("gift", rows["string:${R.string.signing_field_comment}"])
        assertEquals("0.000000005 TON", rows["string:${R.string.signing_field_forward_amount}"])
        assertEquals("0.05 TON", rows["string:${R.string.signing_field_ton_attached}"])
    }

    @Test
    fun unknownContractCallsAreShownAsRawData(): Unit = runBlocking {
        val body = buildCell {
            storeUInt(0x12345678, 32)
            storeUInt(1, 64)
        }

        val result = summaries.summary(chain, request(message(NFT_ITEM, body)), PUBLIC_KEY)

        val rows = result.sections.single().rows.associate { it.title to it.value }
        assertTrue(result.showsRawData)
        assertEquals("string:${R.string.signing_action_contract_call} · 0.05 TON", result.headline)
        assertTrue(rows.containsKey("string:${R.string.signing_field_contract}"))
        assertTrue(result.warnings.contains("string:${R.string.signing_warning_undecoded}"))
    }

    @Test
    fun plainCommentsAreShownWithTheTransfer(): Unit = runBlocking {
        val result = summaries.summary(chain, request(message(NFT_ITEM, comment("thanks"))), PUBLIC_KEY)

        val rows = result.sections.single().rows.associate { it.title to it.value }
        assertEquals("thanks", rows["string:${R.string.signing_field_comment}"])
        assertEquals("string:${R.string.signing_action_transfer}", rows["string:${R.string.signing_field_action}"])
    }

    private fun jettonMessage(wallet: AddrStd, amount: BigInteger) = message(
        wallet,
        buildCell {
            storeUInt(0xf8a7ea5, 32)
            storeUInt(7, 64)
            storeTlb(Coins, Coins.ofNano(amount))
            storeTlb(MsgAddress, RECIPIENT)
            storeTlb(MsgAddress, AddrNone)
            storeBit(false)
            storeTlb(Coins, Coins.ofNano(0))
            storeBit(true)
            storeRef(comment("memo"))
        }
    )

    private fun message(destination: AddrStd, body: Cell) = TonConnectRawMessage(
        destination.toString(userFriendly = true),
        50_000_000L,
        null,
        Base64.getEncoder().encodeToString(BagOfCells(body).toByteArray())
    )

    private fun comment(text: String) = buildCell {
        storeUInt(0, 32)
        storeBytes(text.toByteArray())
    }

    private fun balance(
        wallet: AddrStd,
        master: AddrStd,
        symbol: String,
        decimals: Int
    ) = JettonBalance(
        "1",
        AccountAddress(wallet.toString(userFriendly = false), false, true),
        preview(master, symbol, decimals)
    )

    private fun preview(
        master: AddrStd,
        symbol: String,
        decimals: Int
    ) = JettonPreview(master.toString(userFriendly = false), symbol, symbol, decimals, "", "whitelist")

    private fun request(message: TonConnectRawMessage) =
        TonConnectSignRequest(null, 1_800_000_000L, listOf(message), "-239")

    private companion object {
        val PUBLIC_KEY = ByteArray(32) { 3 }
        val USDT_WALLET = AddrStd(0, ByteArray(32) { 1 })
        val USDT_MASTER = AddrStd(0, ByteArray(32) { 2 })
        val OTHER_WALLET = AddrStd(0, ByteArray(32) { 8 })
        val OTHER_MASTER = AddrStd(0, ByteArray(32) { 9 })
        val NFT_ITEM = AddrStd(0, ByteArray(32) { 4 })
        val RECIPIENT = AddrStd(0, ByteArray(32) { 6 })
        val RESPONSE = AddrStd(0, ByteArray(32) { 7 })
    }
}
