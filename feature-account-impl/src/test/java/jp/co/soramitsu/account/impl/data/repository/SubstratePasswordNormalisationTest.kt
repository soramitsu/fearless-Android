package jp.co.soramitsu.account.impl.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.impl.domain.recovery.WalletIdentityVerifier
import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyIdentity
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.importLegacyGoogleBackupAccount
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The Substrate derivation-path password (`//hard///password`) as every client must read it.
 *
 * `substrate-password-vectors.json` is written by the web repository's
 * `scripts/generate-substrate-password-vectors.py` (Python standard library only, no client code).
 * Its `nfkd` accounts salt the mini secret with NFKD("mnemonic" + password), which Android has
 * always done (fearless-utils `SeedCreator`). The `nfc` and `asTyped` accounts are what Fearless
 * web 4.2.x and 3.0.x derived; for non-ASCII passwords they are other accounts.
 *
 * The host JVM has no sr25519 library (only the app's Android `jniLibs`), so sr25519 is covered by
 * the mini secret every key type starts from; ed25519 and ECDSA run end to end.
 */
class SubstratePasswordNormalisationTest {

    @Test
    fun `the mini secret is salted with the NFKD password for every vector`() {
        for (vector in vectors()) {
            val label = vector["id"].asString
            val password = SubstrateJunctionDecoder.decode(vector["derivationPath"].asString).password
            val seed = SubstrateSeedFactory.deriveSeed32(vector["mnemonic"].asString, password).seed

            assertEquals(label, rule(vector, "nfkd")["miniSecret"].asString, hex(seed))
            if (!vector["rulesAgree"].asBoolean) {
                assertNotEquals(label, rule(vector, "nfc")["miniSecret"].asString, hex(seed))
            }
        }
    }

    @Test
    fun `import derives the NFKD account for every password`() = runTest {
        for (vector in vectors()) {
            for ((cryptoType, key) in CRYPTO_TYPES) {
                val label = "${vector["id"].asString} $key"
                val wallet = create(payload(vector, cryptoType))

                assertEquals(label, account(vector, "nfkd", key, "publicKey"), hex(wallet.substratePublicKey))
                assertEquals(label, account(vector, "nfkd", key, "accountId"), hex(wallet.substrateAccountId))
                if (!vector["rulesAgree"].asBoolean) {
                    assertNotEquals(label, account(vector, "nfc", key, "accountId"), hex(wallet.substrateAccountId))
                }
            }
        }
    }

    @Test
    fun `composed and decomposed passwords open the same account`() = runTest {
        val composed = vector("default24-unicode")
        val decomposed = vector("default24-unicode-decomposed-input")
        assertNotEquals(composed["derivationPath"].asString, decomposed["derivationPath"].asString)

        val first = create(payload(composed, CryptoType.ED25519))
        val second = create(payload(decomposed, CryptoType.ED25519))

        assertArrayEquals(first.substrateAccountId, second.substrateAccountId)
    }

    @Test
    fun `Google Drive restore passes the path verbatim and opens the imported account`() = runTest {
        for (id in listOf("default24-unicode", "default24-unicode-decomposed-input", "import12-katakana")) {
            val vector = vector(id)
            val path = vector["derivationPath"].asString
            val interactor = mock<AccountInteractor>()
            whenever(interactor.createAccount(any())).thenReturn(Result.success(1L))
            val backup = DecryptedBackupAccount(
                name = id,
                address = account(vector, "nfkd", "ed25519", "ss58Generic"),
                mnemonicPhrase = vector["mnemonic"].asString,
                substrateDerivationPath = path,
                ethDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
                cryptoType = CryptoType.ED25519,
                backupAccountType = listOf(BackupAccountType.PASSPHRASE)
            )

            importLegacyGoogleBackupAccount(interactor, backup, "backup password")

            val payload = argumentCaptor<AddAccountPayload>()
            verify(interactor).createAccount(payload.capture())
            val restoring = payload.firstValue as AddAccountPayload.SubstrateOrEvm
            assertArrayEquals(
                id,
                path.toByteArray(Charsets.UTF_8),
                restoring.substrateDerivationPath.toByteArray(Charsets.UTF_8)
            )
            val restored = create(restoring)
            assertEquals(id, account(vector, "nfkd", "ed25519", "accountId"), hex(restored.substrateAccountId))
        }
    }

    @Test
    fun `an import can open the account an earlier browser extension made`() = runTest {
        for (vector in vectors().filterNot { it["rulesAgree"].asBoolean }) {
            for ((rule, key) in EARLIER_RULES) {
                for ((cryptoType, cryptoKey) in CRYPTO_TYPES) {
                    val label = "${vector["id"].asString} $key $cryptoKey"
                    val wallet = create(payload(vector, cryptoType, rule))

                    assertEquals(label, account(vector, key, cryptoKey, "accountId"), hex(wallet.substrateAccountId))
                }
            }
        }
    }

    @Test
    fun `such a wallet's stored words and path re-derive its key, so export and backup keep the phrase`() = runTest {
        for (vector in vectors()) {
            for (rule in SubstratePasswordRule.entries) {
                val label = "${vector["id"].asString} $rule"
                val (wallet, secrets) = createWithSecrets(payload(vector, CryptoType.ED25519, rule))

                val validated = WalletRootSecretValidator.validateSubstrate(
                    encoded = secrets,
                    expectedPublicKey = requireNotNull(wallet.substratePublicKey),
                    expectedCryptoType = CryptoType.ED25519,
                    expectedAccountId = requireNotNull(wallet.substrateAccountId)
                )

                assertEquals(label, RecoveryAssessment.Verified, validated.assessment)
            }
        }
    }

    @Test
    fun `recovery with the words and path reopens a wallet imported under any rule`() = runTest {
        val verifier = WalletIdentityVerifier(mock())
        for (vector in vectors().filterNot { it["rulesAgree"].asBoolean }) {
            for (rule in SubstratePasswordRule.distinctFor(password(vector))) {
                val wallet = create(payload(vector, CryptoType.ED25519, rule))
                val identity = WalletKeyIdentity.Substrate(
                    publicKey = requireNotNull(wallet.substratePublicKey),
                    accountId = requireNotNull(wallet.substrateAccountId),
                    cryptoType = CryptoType.ED25519
                )

                val recovered = verifier.verifyMnemonic(identity, vector["mnemonic"].asString, vector["derivationPath"].asString)

                assertEquals("${vector["id"].asString} $rule", WalletSecretScope.Substrate, recovered.scope)
            }
        }
    }

    @Test
    fun `Google Drive restore opens the account the backup is filed under`() = runTest {
        val vector = vector("default24-unicode")
        val interactor = mock<AccountInteractor>()
        whenever(interactor.createAccount(any())).thenReturn(Result.success(1L))
        val backup = DecryptedBackupAccount(
            name = "Earlier extension wallet",
            address = account(vector, "nfc", "ed25519", "ss58Generic"),
            mnemonicPhrase = vector["mnemonic"].asString,
            substrateDerivationPath = vector["derivationPath"].asString,
            ethDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
            cryptoType = CryptoType.ED25519,
            backupAccountType = listOf(BackupAccountType.PASSPHRASE)
        )

        importLegacyGoogleBackupAccount(interactor, backup, "backup password", substratePasswordRule = SubstratePasswordRule.NFC)

        val payload = argumentCaptor<AddAccountPayload>()
        verify(interactor).createAccount(payload.capture())
        val restoring = payload.firstValue as AddAccountPayload.SubstrateOrEvm
        assertEquals(SubstratePasswordRule.NFC, restoring.substratePasswordRule)
        assertEquals(account(vector, "nfc", "ed25519", "accountId"), hex(create(restoring).substrateAccountId))
    }

    private suspend fun create(payload: AddAccountPayload.SubstrateOrEvm): MetaAccountLocal = createWithSecrets(payload).first

    private suspend fun createWithSecrets(payload: AddAccountPayload.SubstrateOrEvm): Pair<MetaAccountLocal, String> {
        val coordinator = mock<WalletSecretMutationCoordinator>()
        whenever(coordinator.create(any(), any(), any(), anyOrNull())).thenReturn(1L)
        SubstrateOrEvmAccountRepository(metaAccountDao = mock(), walletSecretMutationCoordinator = coordinator)
            .create(payload)
        val wallet = argumentCaptor<MetaAccountLocal>()
        val substrateSecrets = argumentCaptor<String>()
        verify(coordinator).create(wallet.capture(), substrateSecrets.capture(), any(), anyOrNull())
        return wallet.firstValue to substrateSecrets.firstValue
    }

    private fun password(vector: JsonObject) = SubstrateJunctionDecoder.decode(vector["derivationPath"].asString).password

    private fun payload(
        vector: JsonObject,
        cryptoType: CryptoType,
        rule: SubstratePasswordRule = SubstratePasswordRule.NFKD
    ) = AddAccountPayload.SubstrateOrEvm(
        accountName = vector["id"].asString,
        mnemonic = vector["mnemonic"].asString,
        encryptionType = cryptoType,
        substrateDerivationPath = vector["derivationPath"].asString,
        ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
        googleBackupAddress = null,
        isBackedUp = false,
        substratePasswordRule = rule
    )

    private fun rule(vector: JsonObject, rule: String): JsonObject = vector["accounts"].asJsonObject[rule].asJsonObject

    private fun account(vector: JsonObject, rule: String, key: String, field: String): String =
        rule(vector, rule)[key].asJsonObject[field].asString

    private fun hex(bytes: ByteArray?): String = "0x" + Hex.toHexString(requireNotNull(bytes))

    private fun vector(id: String): JsonObject = vectors().single { it["id"].asString == id }

    private fun vectors(): List<JsonObject> {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("substrate-password-vectors.json")) {
            "substrate-password-vectors.json is missing from test resources"
        }
        val document = stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        assertEquals("nfkd", document["canonicalRule"].asString)
        return document["vectors"].asJsonArray.map { it.asJsonObject }
    }

    private companion object {
        val EARLIER_RULES = listOf(
            SubstratePasswordRule.NFC to "nfc",
            SubstratePasswordRule.AS_TYPED to "asTyped"
        )
        val CRYPTO_TYPES = listOf(
            CryptoType.ED25519 to "ed25519",
            CryptoType.ECDSA to "ecdsa"
        )
    }
}
