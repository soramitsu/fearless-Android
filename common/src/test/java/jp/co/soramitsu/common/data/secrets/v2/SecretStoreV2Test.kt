package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema.PrivateKey
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets.SubstrateDerivationPath
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets.SubstrateKeypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.test_shared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val META_ID = 1L
private val ACCOUNT_ID = byteArrayOf(1)

@RunWith(JUnit4::class)
class SecretStoreV2Test {

    private val secretStore = SecretStoreV2(HashMapEncryptedPreferences())

    @Test
    fun `should save and retrieve meta account secrets`() = runBlocking {

        val secrets = createMetaSecrets()

        secretStore.putMetaAccountSecrets(META_ID, secrets)

        val secretsFromStore = secretStore.getMetaAccountSecrets(META_ID)

        requireNotNull(secretsFromStore)
        assertArrayEquals(secrets[SubstrateKeypair][PrivateKey], secretsFromStore[SubstrateKeypair][PrivateKey])
    }

    @Test
    fun `should save and retrieve chain account secrets`() = runBlocking {
        val secrets = createChainSecrets()

        secretStore.putChainAccountSecrets(META_ID, ACCOUNT_ID, secrets)

        val secretsFromStore = secretStore.getChainAccountSecrets(META_ID, ACCOUNT_ID)

        requireNotNull(secretsFromStore)
        assertArrayEquals(secrets[ChainAccountSecrets.Keypair][PrivateKey], secretsFromStore[ChainAccountSecrets.Keypair][PrivateKey])

        val metaSecrets = secretStore.getMetaAccountSecrets(META_ID)

        assertNull("Chain secrets should not overwrite meta account secrets", metaSecrets)
    }

    @Test
    fun `chain secrets should not overwrite meta secrets`() = runBlocking {
        val metaSecrets = createMetaSecrets(derivationPath = "/1")
        val chainSecrets = createChainSecrets(derivationPath = "/2")

        secretStore.putMetaAccountSecrets(metaId = 11, metaSecrets)
        secretStore.putChainAccountSecrets(metaId = 1, accountId= ACCOUNT_ID, chainSecrets)

        val secretsFromStore = secretStore.getMetaAccountSecrets(11)

        requireNotNull(secretsFromStore)
        assertEquals( metaSecrets[SubstrateDerivationPath], secretsFromStore[SubstrateDerivationPath])
    }

    @Test
    fun `should delete secrets`() = runBlocking {
        val metaSecrets = createMetaSecrets()
        val chainSecrets = createChainSecrets()

        secretStore.putMetaAccountSecrets(metaId = META_ID, metaSecrets)
        secretStore.putChainAccountSecrets(metaId = META_ID, accountId = ACCOUNT_ID, chainSecrets)

        secretStore.clearSecrets(META_ID, chainAccountIds = listOf(ACCOUNT_ID))

        val metaSecretsLocal = secretStore.getMetaAccountSecrets(META_ID)
        assertNull(metaSecretsLocal)

        val chainSecretsLocal = secretStore.getChainAccountSecrets(META_ID, ACCOUNT_ID)
        assertNull(chainSecretsLocal)
    }

    private fun createMetaSecrets(
        derivationPath: String? = null,
    ): EncodableStruct<MetaAccountSecrets> {
        return MetaAccountSecrets(
            substrateDerivationPath = derivationPath,
            substrateKeyPair = Keypair(
                privateKey = byteArrayOf(),
                publicKey = byteArrayOf()
            )
        )
    }

    private fun createChainSecrets(
        derivationPath: String? = null,
    ): EncodableStruct<ChainAccountSecrets> {
        return ChainAccountSecrets(
            derivationPath = derivationPath,
            keyPair = Keypair(
                privateKey = byteArrayOf(),
                publicKey = byteArrayOf()
            )
        )
    }
}
