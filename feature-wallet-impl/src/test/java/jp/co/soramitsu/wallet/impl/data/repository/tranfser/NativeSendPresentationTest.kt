package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlannerException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSelectionException
import jp.co.soramitsu.common.data.network.solana.SolanaAccountPreflightException
import jp.co.soramitsu.common.data.network.solana.SolanaPaymentQuoteException
import jp.co.soramitsu.common.data.network.send.WalletSendJournalException
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendDraftGuard
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendError
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NativeSendPresentationTest {
    @Test fun `native reviewed amounts retain lamports and full unsigned token units without abbreviation`() {
        val format = jp.co.soramitsu.wallet.impl.presentation.send.NativeSendFormat
        assertEquals("0.000000001 SOL", format.amount(BigDecimal("0.000000001"), "SOL"))
        assertEquals("0.000005005 SOL", format.amount(BigDecimal("0.000005005"), "SOL"))
        assertEquals("18446744073709.551615 LOCAL", format.amount(BigDecimal("18446744073709.551615"), "LOCAL"))
        assertEquals("0.00439718 BTC", format.amount(BigDecimal("0.0043971800"), "BTC"))
        assertEquals("0 BTC", format.amount(BigDecimal.ZERO, "BTC"))
    }

    @Test fun `confirmation preserves remote pause copy and redacts untrusted errors`() {
        val resources = io.mockk.mockk<jp.co.soramitsu.common.resources.ResourceManager>()
        io.mockk.every { resources.getString(R.string.fee_calculation_error) } returns "Could not calculate the fee"
        val paused = jp.co.soramitsu.wallet.impl.domain.SendPausedException("Bitcoin sends paused", "Try again later")
        assertSame(paused, NativeSendError.display(paused, resources))
        assertEquals("Could not calculate the fee", NativeSendError.display(Exception("untrusted provider payload"), resources).message)
        assertEquals(R.string.pin_recovery_view_only_intro,
            NativeSendError.message(jp.co.soramitsu.common.data.storage.pin.WalletPinRecoveryRequiredException()))
    }

    @Test fun `confirmation cannot switch wallet before first quote even after A B A`() {
        val draft = TransferDraft(BigDecimal.ONE, BigDecimal.ZERO, AssetPayload("bitcoin", "BTC"), "recipient", null, null,
            walletId = 1, walletSelectionEpoch = WalletSelectionEpoch.snapshot())
        NativeSendDraftGuard.requireCurrent(draft, 1)
        assertThrows(NativeSendException::class.java) { NativeSendDraftGuard.requireCurrent(draft, 2) }
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        assertThrows(NativeSendException::class.java) { NativeSendDraftGuard.requireCurrent(draft, 1) }
    }

    @Test fun `typed failures use actionable copy and never show provider payloads`() {
        assertEquals(R.string.send_existing_pending, NativeSendError.message(NativeSendException(NativeSendException.Code.PAYMENT_PENDING)))
        assertEquals(R.string.send_bitcoin_dust, NativeSendError.message(BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.AMOUNT_BELOW_DUST)))
        assertEquals(R.string.send_bitcoin_address_help, NativeSendError.message(BitcoinSendPlannerException(BitcoinSendPlannerException.Code.INVALID_RECIPIENT_ADDRESS)))
        assertEquals(R.string.send_solana_rent, NativeSendError.message(SolanaAccountPreflightException(SolanaAccountPreflightException.Code.SENDER_BELOW_RENT)))
        assertEquals(R.string.send_review_changed, NativeSendError.message(SolanaPaymentQuoteException(SolanaPaymentQuoteException.Code.BLOCKHASH_EXPIRED)))
        assertEquals(R.string.send_storage_unavailable, NativeSendError.message(WalletSendJournalException(WalletSendJournalException.Code.CORRUPT_RECORD)))
        assertEquals(R.string.fee_calculation_error, NativeSendError.message(IllegalArgumentException("untrusted provider text")))
        // Every declared protocol code has a safe resource mapping, including future generic failures.
        NativeSendException.Code.entries.forEach { assertTrue(NativeSendError.message(NativeSendException(it)) != 0) }
        BitcoinSendPlannerException.Code.entries.forEach { assertTrue(NativeSendError.message(BitcoinSendPlannerException(it)) != 0) }
        BitcoinUtxoSelectionException.Code.entries.forEach { assertTrue(NativeSendError.message(BitcoinUtxoSelectionException(it)) != 0) }
        SolanaAccountPreflightException.Code.entries.forEach { assertTrue(NativeSendError.message(SolanaAccountPreflightException(it)) != 0) }
        SolanaPaymentQuoteException.Code.entries.forEach { assertTrue(NativeSendError.message(SolanaPaymentQuoteException(it)) != 0) }
    }
}
