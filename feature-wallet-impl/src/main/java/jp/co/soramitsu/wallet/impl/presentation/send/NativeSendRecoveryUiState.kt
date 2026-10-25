package jp.co.soramitsu.wallet.impl.presentation.send

import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecoverySnapshot

/** Transient dialog state; identity and revision stay bound to the original opened wallet. */
data class NativeSendRecoveryUiState(
    val snapshot: NativeSendRecoverySnapshot,
    val busy: Boolean = false,
    val error: String? = null
)
