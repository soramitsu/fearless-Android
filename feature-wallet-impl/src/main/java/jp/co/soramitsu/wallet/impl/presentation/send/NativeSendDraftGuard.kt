package jp.co.soramitsu.wallet.impl.presentation.send

import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendException

/** Preserves the wallet that opened confirmation, before its first asynchronous quote exists. */
object NativeSendDraftGuard {
    fun requireCurrent(draft: TransferDraft, currentWalletId: Long) {
        val expected = draft.walletId ?: return // Existing non-native callers carry no wallet binding.
        if (expected != currentWalletId || draft.walletSelectionEpoch?.let(WalletSelectionEpoch::isCurrent) != true) {
            throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
        }
    }
}
