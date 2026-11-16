package jp.co.soramitsu.app.root.presentation.emergency

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryFieldType
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryKeyKind
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryState
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyRecoveryLabelsTest {
    @Test
    fun everyKeyKindHasAPlainLanguageNameAndEarlyFormatsShareOne() {
        val expected = mapOf(
            EmergencyRecoveryKeyKind.SUBSTRATE to R.string.wallet_emergency_kind_substrate,
            EmergencyRecoveryKeyKind.ETHEREUM to R.string.wallet_emergency_kind_ethereum,
            EmergencyRecoveryKeyKind.TON to R.string.wallet_emergency_kind_ton,
            EmergencyRecoveryKeyKind.LEGACY_WALLET to R.string.wallet_emergency_kind_legacy,
            EmergencyRecoveryKeyKind.NETWORK_ACCOUNT to R.string.wallet_emergency_kind_network,
            EmergencyRecoveryKeyKind.LEGACY_V1 to R.string.wallet_emergency_kind_early,
            EmergencyRecoveryKeyKind.LEGACY_V04 to R.string.wallet_emergency_kind_early,
            EmergencyRecoveryKeyKind.LEGACY_V04_TEXT to R.string.wallet_emergency_kind_early
        )
        assertEquals(EmergencyRecoveryKeyKind.entries.toSet(), expected.keys)
        expected.forEach { (kind, label) -> assertEquals(kind.name, label, EmergencyRecoveryLabels.kind(kind)) }
    }

    @Test
    fun everyStorageStateIsNamed() {
        assertEquals(R.string.wallet_emergency_state_active, EmergencyRecoveryLabels.state(EmergencyRecoveryState.ACTIVE))
        assertEquals(R.string.wallet_emergency_state_set_aside, EmergencyRecoveryLabels.state(EmergencyRecoveryState.SET_ASIDE))
        assertEquals(R.string.wallet_emergency_state_archived, EmergencyRecoveryLabels.state(EmergencyRecoveryState.ARCHIVED))
    }

    @Test
    fun everyRevealedFieldGetsItsOwnDistinctLabel() {
        val expected = mapOf(
            EmergencyRecoveryFieldType.RECOVERY_PHRASE to R.string.wallet_emergency_field_phrase,
            EmergencyRecoveryFieldType.TON_PHRASE to R.string.wallet_emergency_field_ton_phrase,
            EmergencyRecoveryFieldType.ENTROPY to R.string.wallet_emergency_field_entropy,
            EmergencyRecoveryFieldType.RAW_SEED to R.string.wallet_emergency_field_seed,
            EmergencyRecoveryFieldType.DERIVATION_PATH to R.string.wallet_emergency_field_path,
            EmergencyRecoveryFieldType.ETHEREUM_DERIVATION_PATH to R.string.wallet_emergency_field_ethereum_path,
            EmergencyRecoveryFieldType.PRIVATE_KEY to R.string.wallet_emergency_field_private_key,
            EmergencyRecoveryFieldType.ETHEREUM_PRIVATE_KEY to R.string.wallet_emergency_field_ethereum_private_key,
            EmergencyRecoveryFieldType.TON_PRIVATE_KEY to R.string.wallet_emergency_field_ton_private_key,
            EmergencyRecoveryFieldType.NONCE to R.string.wallet_emergency_field_nonce,
            EmergencyRecoveryFieldType.STORED_DATA to R.string.wallet_emergency_field_stored
        )
        assertEquals(EmergencyRecoveryFieldType.entries.toSet(), expected.keys)
        expected.forEach { (type, label) -> assertEquals(type.name, label, EmergencyRecoveryLabels.field(type)) }
        assertEquals(expected.size, expected.values.toSet().size)
    }
}
