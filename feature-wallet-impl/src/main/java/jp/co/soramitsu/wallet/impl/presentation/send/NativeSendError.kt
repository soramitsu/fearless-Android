package jp.co.soramitsu.wallet.impl.presentation.send

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlannerException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSelectionException
import jp.co.soramitsu.common.data.network.send.WalletSendJournalException
import jp.co.soramitsu.common.data.network.solana.SolanaAccountPreflightException
import jp.co.soramitsu.common.data.network.solana.SolanaPaymentQuoteException
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendException

/** Maps typed protocol failures to actionable localized copy without displaying provider payloads. */
object NativeSendError {
    /** Preserves the trusted remote-veto title while redacting all provider errors. */
    fun display(error: Throwable, resources: jp.co.soramitsu.common.resources.ResourceManager): Throwable =
        if (error is jp.co.soramitsu.wallet.impl.domain.SendPausedException) error
        else Exception(resources.getString(message(error)))

    fun message(error: Throwable): Int = when (error) {
        is NativeSendException -> when (error.code) {
            NativeSendException.Code.PAYMENT_PENDING -> R.string.send_existing_pending
            NativeSendException.Code.PAYMENT_FAILED -> R.string.send_failed_message
            NativeSendException.Code.REVIEW_REQUIRED, NativeSendException.Code.WALLET_CHANGED, NativeSendException.Code.QUOTE_EXPIRED -> R.string.send_review_changed
            NativeSendException.Code.UNSUPPORTED_ACCOUNT -> R.string.send_unsupported_account
            NativeSendException.Code.STORAGE_UNAVAILABLE -> R.string.send_storage_unavailable
            else -> R.string.fee_calculation_error
        }
        is jp.co.soramitsu.common.data.storage.pin.WalletPinRecoveryRequiredException -> R.string.pin_recovery_view_only_intro
        is WalletSendJournalException -> R.string.send_storage_unavailable
        is BitcoinSendPlannerException -> when (error.code) {
            BitcoinSendPlannerException.Code.INVALID_RECIPIENT_ADDRESS -> R.string.send_bitcoin_address_help
            BitcoinSendPlannerException.Code.NO_SPENDABLE_UTXOS -> R.string.send_insufficient_spendable
            BitcoinSendPlannerException.Code.REVIEWED_INPUT_CHANGED -> R.string.send_review_changed
            else -> R.string.fee_calculation_error
        }
        is BitcoinUtxoSelectionException -> when (error.code) {
            BitcoinUtxoSelectionException.Code.AMOUNT_BELOW_DUST -> R.string.send_bitcoin_dust
            BitcoinUtxoSelectionException.Code.INSUFFICIENT_FUNDS, BitcoinUtxoSelectionException.Code.UTXOS_REQUIRED -> R.string.send_insufficient_spendable
            else -> R.string.fee_calculation_error
        }
        is SolanaPaymentQuoteException -> when (error.code) {
            SolanaPaymentQuoteException.Code.BLOCKHASH_EXPIRED, SolanaPaymentQuoteException.Code.REVIEW_CHANGED -> R.string.send_review_changed
            SolanaPaymentQuoteException.Code.INSUFFICIENT_TOKEN_BALANCE -> R.string.send_insufficient_spendable
            SolanaPaymentQuoteException.Code.UNSUPPORTED_TOKEN -> R.string.send_unsupported_account
            else -> R.string.fee_calculation_error
        }
        is SolanaAccountPreflightException -> when (error.code) {
            SolanaAccountPreflightException.Code.INSUFFICIENT_BALANCE -> R.string.send_insufficient_spendable
            SolanaAccountPreflightException.Code.RECIPIENT_BELOW_RENT, SolanaAccountPreflightException.Code.SENDER_BELOW_RENT -> R.string.send_solana_rent
            else -> R.string.send_solana_account_invalid
        }
        else -> R.string.fee_calculation_error
    }
}
