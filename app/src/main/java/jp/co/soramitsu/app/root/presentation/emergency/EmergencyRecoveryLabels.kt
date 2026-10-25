package jp.co.soramitsu.app.root.presentation.emergency

import androidx.annotation.StringRes
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryFieldType
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryKeyKind
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryState

/** Plain-language names for what the emergency export lists. */
internal object EmergencyRecoveryLabels {

    @StringRes
    fun kind(kind: EmergencyRecoveryKeyKind): Int = when (kind) {
        EmergencyRecoveryKeyKind.SUBSTRATE -> R.string.wallet_emergency_kind_substrate
        EmergencyRecoveryKeyKind.ETHEREUM -> R.string.wallet_emergency_kind_ethereum
        EmergencyRecoveryKeyKind.TON -> R.string.wallet_emergency_kind_ton
        EmergencyRecoveryKeyKind.LEGACY_WALLET -> R.string.wallet_emergency_kind_legacy
        EmergencyRecoveryKeyKind.NETWORK_ACCOUNT -> R.string.wallet_emergency_kind_network
        EmergencyRecoveryKeyKind.LEGACY_V1,
        EmergencyRecoveryKeyKind.LEGACY_V04,
        EmergencyRecoveryKeyKind.LEGACY_V04_TEXT -> R.string.wallet_emergency_kind_early
    }

    @StringRes
    fun state(state: EmergencyRecoveryState): Int = when (state) {
        EmergencyRecoveryState.ACTIVE -> R.string.wallet_emergency_state_active
        EmergencyRecoveryState.SET_ASIDE -> R.string.wallet_emergency_state_set_aside
        EmergencyRecoveryState.ARCHIVED -> R.string.wallet_emergency_state_archived
    }

    @StringRes
    fun field(type: EmergencyRecoveryFieldType): Int = when (type) {
        EmergencyRecoveryFieldType.RECOVERY_PHRASE -> R.string.wallet_emergency_field_phrase
        EmergencyRecoveryFieldType.TON_PHRASE -> R.string.wallet_emergency_field_ton_phrase
        EmergencyRecoveryFieldType.ENTROPY -> R.string.wallet_emergency_field_entropy
        EmergencyRecoveryFieldType.RAW_SEED -> R.string.wallet_emergency_field_seed
        EmergencyRecoveryFieldType.DERIVATION_PATH -> R.string.wallet_emergency_field_path
        EmergencyRecoveryFieldType.ETHEREUM_DERIVATION_PATH -> R.string.wallet_emergency_field_ethereum_path
        EmergencyRecoveryFieldType.PRIVATE_KEY -> R.string.wallet_emergency_field_private_key
        EmergencyRecoveryFieldType.ETHEREUM_PRIVATE_KEY -> R.string.wallet_emergency_field_ethereum_private_key
        EmergencyRecoveryFieldType.TON_PRIVATE_KEY -> R.string.wallet_emergency_field_ton_private_key
        EmergencyRecoveryFieldType.NONCE -> R.string.wallet_emergency_field_nonce
        EmergencyRecoveryFieldType.STORED_DATA -> R.string.wallet_emergency_field_stored
    }
}
