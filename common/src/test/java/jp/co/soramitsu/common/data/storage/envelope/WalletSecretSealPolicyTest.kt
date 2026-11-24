package jp.co.soramitsu.common.data.storage.envelope

import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretSealPolicyTest {

    @Test
    fun `every wallet secret namespace and its copies is sealable`() {
        val expected = mapOf(
            "7:SUBSTRATE_SECRETS" to WalletSecretEnvelopeScope.SUBSTRATE_ROOT,
            "7:ETHEREUM_SECRETS" to WalletSecretEnvelopeScope.ETHEREUM_ROOT,
            "7:TON_SECRETS" to WalletSecretEnvelopeScope.TON_ROOT,
            "7:ACCESS_SECRETS" to WalletSecretEnvelopeScope.LEGACY_META_ACCOUNT,
            "7:0a1b2c3d4e5f60718293a4b5c6d7e8f901234567:ACCESS_SECRETS" to WalletSecretEnvelopeScope.CHAIN_ACCOUNT,
            "security_source_5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY" to WalletSecretEnvelopeScope.LEGACY_V1,
            "private_5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY" to WalletSecretEnvelopeScope.LEGACY_V04_SIGNING,
            "seed_5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY" to WalletSecretEnvelopeScope.LEGACY_V04_TEXT,
            "entropy_x" to WalletSecretEnvelopeScope.LEGACY_V04_TEXT,
            "derivation_x" to WalletSecretEnvelopeScope.LEGACY_V04_TEXT
        )
        for ((active, scope) in expected) {
            val target = WalletSecretSealPolicy.classify(active)
            assertEquals(active, scope, target?.scope)
            assertEquals(active, target?.logicalKey)
            assertEquals(WalletSecretSealPolicy.Location.ACTIVE, target?.location)

            val quarantined = WalletSecretSealPolicy.classify(WalletSecretQuarantine.keyFor(active))
            assertEquals(scope, quarantined?.scope)
            assertEquals(active, quarantined?.logicalKey)
            assertEquals(WalletSecretSealPolicy.Location.QUARANTINE, quarantined?.location)

            for (slot in listOf(0, 3)) {
                val archived = WalletSecretSealPolicy.classify(WalletSecretArchive.keyFor(active, slot))
                assertEquals(scope, archived?.scope)
                assertEquals(active, archived?.logicalKey)
                assertEquals(WalletSecretSealPolicy.Location.ARCHIVE, archived?.location)
            }
        }
    }

    @Test
    fun `legacy quarantine names are sealable only as quarantine or archive copies`() {
        val v1 = WalletSecretQuarantine.legacyV1KeyForMetaId(7, byteArrayOf(1, 2, 3))
        val v04 = WalletSecretQuarantine.legacyV04KeyForMetaId(7, byteArrayOf(1, 2, 3))
        val legacy = WalletSecretQuarantine.legacyV1KeyForPublicKey(byteArrayOf(4, 5))
        assertEquals(WalletSecretEnvelopeScope.LEGACY_V1, WalletSecretSealPolicy.classify(v1)?.scope)
        assertEquals(7L, WalletSecretSealPolicy.classify(v1)?.metaId)
        assertEquals(WalletSecretEnvelopeScope.LEGACY_V04_SIGNING, WalletSecretSealPolicy.classify(v04)?.scope)
        assertEquals(WalletSecretEnvelopeScope.LEGACY_V1, WalletSecretSealPolicy.classify(legacy)?.scope)
        val archiveName = v1.removePrefix(WalletSecretQuarantine.KEY_PREFIX)
        assertEquals(WalletSecretEnvelopeScope.LEGACY_V1, WalletSecretSealPolicy.classify(WalletSecretArchive.keyFor(archiveName))?.scope)
        assertNull(WalletSecretSealPolicy.classify(archiveName))
    }

    @Test
    fun `the PIN, sessions, journals, markers and other preferences are never sealed`() {
        val never = listOf(
            WalletMasterKeyAttestation.PIN_CODE_KEY,
            WalletSecretArchive.keyFor(WalletMasterKeyAttestation.PIN_CODE_KEY),
            WalletSecretArchive.keyFor(WalletMasterKeyAttestation.PIN_CODE_KEY, 2),
            WalletMasterKeyAttestation.SENTINEL_KEY,
            WalletSecretMutationJournalStore.JOURNAL_KEY,
            TonConnectStorageKeys.MUTATION_JOURNAL_KEY,
            TonConnectStorageKeys.legacy("ab".repeat(32)),
            WalletSecretQuarantine.keyFor(TonConnectStorageKeys.legacy("ab".repeat(32))),
            WalletPublicIdentityRecovery.keyFor(7, "7:SUBSTRATE_SECRETS"),
            WalletSecretKeyring.KEY_PREFIX + "current",
            WalletSecretKeyring.sealingKey(ByteArray(16) { 1 }),
            WalletSecretKeyring.pinKey(ByteArray(16) { 1 }),
            "biometric_pin_vault_v1",
            "portable_wallet_cohort_journal_v1",
            "auth_type",
            "0:SUBSTRATE_SECRETS",
            "07:SUBSTRATE_SECRETS",
            "7:BITCOIN_SECRETS",
            "7:0A:ACCESS_SECRETS",
            "wc_session_key",
            ""
        )
        never.forEach { assertFalse(it, WalletSecretSealPolicy.isSealable(it)) }
    }

    @Test
    fun `scanned prefixes cover every sealable name`() {
        val names = listOf(
            "7:SUBSTRATE_SECRETS", "12:ab:ACCESS_SECRETS", "security_source_x", "private_x", "seed_x", "entropy_x",
            "derivation_x", WalletSecretQuarantine.keyFor("7:TON_SECRETS"), WalletSecretArchive.keyFor("7:TON_SECRETS")
        )
        names.forEach { name ->
            assertTrue(name, WalletSecretSealPolicy.isSealable(name))
            assertTrue(name, WalletSecretSealPolicy.SCANNED_PREFIXES.any(name::startsWith))
        }
    }
}
