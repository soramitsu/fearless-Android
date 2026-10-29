package jp.co.soramitsu.walletconnect.impl.presentation.summary

import co.jp.soramitsu.feature_walletconnect_impl.R
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainAmount
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainCall
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainDestination
import jp.co.soramitsu.walletconnect.impl.presentation.CrossChainRetirement
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotRefusal
import jp.co.soramitsu.walletconnect.impl.presentation.ethereumChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class WalletConnectSummaryPresenterTest {
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
        every {
            getString(any(), *anyVararg())
        } answers { "string:${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}" }
    }
    private val presenter = WalletConnectSummaryPresenter(resources) { "unix:$it" }

    @Test
    fun `unknown token approval shows spender contract unlimited warning and raw units`() {
        val state = presenter.present(
            ethereumChain,
            WalletConnectRequestSummary.EvmTransaction(
            TOKEN, BigInteger.TEN, EvmTokenCall.Approve(SPENDER, BigInteger.ONE.shiftLeft(256) - BigInteger.ONE), "0x095ea7b3"
        )
        )
        val values = state.sections.flatMap { it.rows }.map { it.value }
        assertTrue(values.contains(SPENDER))
        assertTrue(values.any { it.contains(TOKEN) })
        assertTrue(values.any { it.contains("string:${R.string.signing_value_unlimited}") })
        assertTrue(values.any { it.endsWith(":10") })
        assertTrue(state.warnings.contains("string:${R.string.signing_warning_unlimited_approval}"))
        assertTrue(state.sections.flatMap { it.rows }.any { it.isWarning })
    }

    @Test
    fun `unknown calldata and typed messages offer raw data fallback`() {
        val unknown = presenter.present(ethereumChain, WalletConnectRequestSummary.EvmTransaction(TOKEN, BigInteger.ONE, null, "0xdeadbeef"))
        assertTrue(unknown.showsRawData)
        assertTrue(unknown.sections.flatMap { it.rows }.any { it.value == "0xdeadbeef" })
        assertTrue(presenter.present(ethereumChain, WalletConnectRequestSummary.Undecoded).showsRawData)
        assertTrue(presenter.present(ethereumChain, WalletConnectRequestSummary.TypedData(TypedDataSummary("Mail", null, null))).showsRawData)
    }

    @Test
    fun `permit summary shows token spender chain contract deadline and warnings`() {
        val typed = TypedDataSummary(
            "Permit", TypedDataDomain("Token", "1", BigInteger.ONE, TOKEN),
            TypedDataPermit.TokenAllowance(TOKEN, SPENDER, BigInteger.TEN, BigInteger("1800000000"))
        )
        val state = presenter.present(ethereumChain, WalletConnectRequestSummary.TypedData(typed))
        val values = state.sections.flatMap { it.rows }.map { it.value }
        assertTrue(values.contains(SPENDER))
        assertTrue(values.contains(TOKEN))
        assertTrue(values.contains("Token v1"))
        assertTrue(values.contains("unix:1800000000"))
        assertTrue(state.warnings.contains("string:${R.string.signing_warning_permit}"))
        assertFalse(state.showsRawData)
    }

    @Test
    fun `Permit2 batches have separate token rows and witness remains raw`() {
        val tokens = listOf(
            TypedDataPermit.Permit2Allowance.Allowance(TOKEN, BigInteger.ONE, BigInteger.TEN),
            TypedDataPermit.Permit2Allowance.Allowance(SPENDER, BigInteger.TWO, BigInteger.ONE)
        )
        val domain = TypedDataDomain("Permit2", null, BigInteger.ONE, TOKEN)
        val state = presenter.present(
            ethereumChain,
            WalletConnectRequestSummary.TypedData(
                TypedDataSummary(
                    "PermitBatch", domain,
            TypedDataPermit.Permit2Allowance(SPENDER, tokens, BigInteger.TEN)
                )
            )
        )
        assertEquals(3, state.sections.size)
        val witness = presenter.present(
            ethereumChain,
            WalletConnectRequestSummary.TypedData(
                TypedDataSummary(
                    "PermitWitnessTransferFrom", domain,
            TypedDataPermit.Permit2Transfer(SPENDER, listOf(TypedDataPermit.Permit2Transfer.Transfer(TOKEN, BigInteger.ONE)), BigInteger.TEN, true)
                )
            )
        )
        assertTrue(witness.showsRawData)
    }

    @Test
    fun `balances transfer and nested batch retain complete recipient and amount`() {
        val transfer = PolkadotCallSummary(
            "Balances", "transfer_keep_alive",
            listOf(
            PolkadotArgument("dest", PolkadotValue.Account("complete recipient")),
            PolkadotArgument("value", PolkadotValue.NativeAmount(BigInteger.TEN))
            )
        )
        val state = presenter.present(ethereumChain, WalletConnectRequestSummary.PolkadotTransaction(transfer, BigInteger.ONE, true))
        assertTrue(state.sections.flatMap { it.rows }.any { it.value == "complete recipient" })
        assertFalse(state.showsRawData)
        val batch = PolkadotCallSummary("Utility", "batch", listOf(PolkadotArgument("calls", PolkadotValue.Calls(listOf(transfer)))))
        assertEquals(2, presenter.present(ethereumChain, WalletConnectRequestSummary.PolkadotTransaction(batch, BigInteger.ZERO, false)).sections.size)
        assertTrue(presenter.present(ethereumChain, WalletConnectRequestSummary.PolkadotTransaction(null, BigInteger.ZERO, false)).showsRawData)
    }

    @Test
    fun `a refused transaction says why first and cannot be signed`() {
        val transfer = PolkadotCallSummary("Balances", "transfer_keep_alive", emptyList())
        val refused = presenter.present(
            ethereumChain,
            WalletConnectRequestSummary.PolkadotTransaction(transfer, BigInteger.ZERO, false, PolkadotRefusal.OtherRuntime)
        )

        assertTrue(refused.blocksSigning)
        assertEquals("string:${R.string.signing_refused_other_runtime}", refused.warnings.first())
        assertEquals("string:${R.string.signing_refused_other_runtime}", refused.headline)
        assertFalse(presenter.present(ethereumChain, WalletConnectRequestSummary.PolkadotTransaction(transfer, BigInteger.ZERO, false)).blocksSigning)
    }

    @Test
    fun `a paused request is headed by the pause, still shows what it asks and cannot be signed`() {
        val transfer = WalletConnectRequestSummary.EvmTransaction(TOKEN, BigInteger.TEN, null, null)

        val paused = presenter.present(ethereumChain, transfer, paused = true)

        assertTrue(paused.blocksSigning)
        assertEquals("string:${R.string.signing_refused_paused}", paused.headline)
        assertEquals("string:${R.string.signing_refused_paused}", paused.warnings.first())
        assertEquals(presenter.present(ethereumChain, transfer).sections, paused.sections)
    }

    @Test
    fun `a paused request that is also refused says the pause first and keeps the refusal`() {
        val transfer = PolkadotCallSummary("Balances", "transfer_keep_alive", emptyList())
        val refused = WalletConnectRequestSummary.PolkadotTransaction(transfer, BigInteger.ZERO, false, PolkadotRefusal.OtherRuntime)

        val paused = presenter.present(ethereumChain, refused, paused = true)

        assertTrue(paused.blocksSigning)
        assertEquals("string:${R.string.signing_refused_paused}", paused.headline)
        assertEquals(
            listOf("string:${R.string.signing_refused_paused}", "string:${R.string.signing_refused_other_runtime}"),
            paused.warnings.take(2)
        )
    }

    @Test
    fun `cross-chain calls show destination beneficiary and amount with warnings`() {
        val teleport = CrossChainCall(
            name = "XcmPallet.limited_teleport_assets",
            destination = CrossChainDestination(paraId = BigInteger.valueOf(1000)),
            beneficiary = "beneficiary",
            amounts = listOf(CrossChainAmount(BigInteger.TEN, null, "{parents: 0, interior: Here}")),
            isHighRisk = false
        )
        val call = PolkadotCallSummary("XcmPallet", "limited_teleport_assets", emptyList())

        val state = presenter.present(ethereumChain, WalletConnectRequestSummary.PolkadotTransaction(call, BigInteger.ZERO, false, crossChainCalls = listOf(teleport)))

        val rows = state.sections.flatMap { it.rows }.associate { it.title to it.value }
        assertEquals("string:${R.string.signing_value_parachain}:1000", rows["string:${R.string.signing_field_destination}"])
        assertEquals("beneficiary", rows["string:${R.string.signing_field_beneficiary}"])
        assertTrue(rows.getValue("string:${R.string.common_amount}").startsWith("string:${R.string.signing_value_raw_units}:10"))
        assertTrue(state.headline.startsWith("string:${R.string.signing_section_cross_chain}"))
        assertTrue(state.warnings.contains("string:${R.string.signing_warning_cross_chain}"))
        assertFalse(state.warnings.contains("string:${R.string.signing_warning_xcm_program}"))
        assertFalse(state.blocksSigning)

        val program = presenter.present(
            ethereumChain,
            WalletConnectRequestSummary.PolkadotTransaction(
                call,
                BigInteger.ZERO,
                false,
                crossChainCalls = listOf(teleport.copy(isHighRisk = true, destination = null, beneficiary = null))
            )
        )
        assertTrue(program.warnings.contains("string:${R.string.signing_warning_xcm_program}"))
        assertTrue(program.sections.flatMap { it.rows }.any { it.value == "string:${R.string.signing_value_set_by_xcm}" })
    }

    @Test
    fun `a transfer to a retired destination names it in the refusal`() {
        val call = PolkadotCallSummary("XcmPallet", "send", emptyList())
        fun refused(retirement: CrossChainRetirement) = presenter.present(
            ethereumChain,
            WalletConnectRequestSummary.PolkadotTransaction(
                call,
                BigInteger.ZERO,
                false,
                PolkadotRefusal.RetiredDestination,
                listOf(CrossChainCall("XcmPallet.send", null, null, emptyList(), isHighRisk = true, retirement = retirement))
            )
        )

        val moonbeam = refused(CrossChainRetirement.Destination("Moonbeam"))
        assertEquals("string:${R.string.signing_refused_destination}:Moonbeam", moonbeam.warnings.first())
        assertTrue(moonbeam.blocksSigning)
        assertEquals("string:${R.string.signing_refused_relay_reserve}", refused(CrossChainRetirement.RelayReserve).warnings.first())
    }

    @Test
    fun `high requested gas price and limit show exact uncapped maximum fee`() {
        val price = BigInteger.ONE.shiftLeft(200)
        val limit = BigInteger.valueOf(30_000_000)
        val summary = WalletConnectRequestSummary.EvmTransaction(TOKEN, BigInteger.ONE, null, null, price, limit)
        val result = presenter.present(ethereumChain, summary)
        val rows = result.sections.flatMap { it.rows }
        assertEquals(limit.toString(), rows.single { it.title == "string:${R.string.signing_field_gas_limit}" }.value)
        assertTrue(rows.single { it.title == "string:${R.string.signing_field_gas_price}" }.value.endsWith(":$price"))
        assertTrue(rows.single { it.title == "string:${R.string.signing_field_max_fee}" }.value.endsWith(":${price * limit}"))
        assertFalse(result.showsRawData)
        assertTrue(presenter.present(ethereumChain, summary.copy(gasPrice = null)).showsRawData)
    }

    private companion object {
        const val TOKEN = "0x1111111111111111111111111111111111111111"
        const val SPENDER = "0x2222222222222222222222222222222222222222"
    }
}
