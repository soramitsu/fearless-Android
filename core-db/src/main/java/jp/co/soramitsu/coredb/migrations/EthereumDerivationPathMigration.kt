package jp.co.soramitsu.coredb.migrations

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.coredb.model.chain.MetaAccountLocal
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.shared_utils.encrypt.seed.ethereum.EthereumSeedFactory
import kotlinx.coroutines.runBlocking

class EthereumDerivationPathMigration(private val storeV2: SecretStoreV2) : Migration(31, 32) {

    override fun migrate(database: SupportSQLiteDatabase) = runBlocking {
        val metaIds = getAccounts(database)

        val ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        val decodedEthereumDerivationPath = BIP32JunctionDecoder.decode(ethereumDerivationPath)

        metaIds.forEach { metaId ->
            val secrets = storeV2.getMetaAccountSecrets(metaId) ?: return@forEach

            val oldEthPubKey = secrets[MetaAccountSecrets.EthereumKeypair]?.get(KeyPairSchema.PublicKey)

            val entropy = secrets[MetaAccountSecrets.Entropy] ?: return@forEach
            val mnemonic = MnemonicCreator.fromEntropy(entropy.clone())

            val ethereumSeed = EthereumSeedFactory.deriveSeed32(mnemonic.words, password = decodedEthereumDerivationPath.password).seed
            val newEthereumKeypair = EthereumKeypairFactory.generate(ethereumSeed, junctions = decodedEthereumDerivationPath.junctions)

            if (oldEthPubKey.contentEquals(newEthereumKeypair.publicKey)) {
                return@forEach
            }

            val substrateKeypair = secrets[MetaAccountSecrets.SubstrateKeypair].let { struct ->
                Keypair(publicKey = struct[KeyPairSchema.PublicKey], privateKey = struct[KeyPairSchema.PrivateKey], nonce = struct[KeyPairSchema.Nonce])
            }
            val seed = secrets[MetaAccountSecrets.Seed]
            val substrateDerivationPath = secrets[MetaAccountSecrets.SubstrateDerivationPath]

            val newSecrets = MetaAccountSecrets(
                substrateKeyPair = substrateKeypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = substrateDerivationPath,
                ethereumKeypair = newEthereumKeypair,
                ethereumDerivationPath = ethereumDerivationPath
            )

            storeV2.clearSecrets(metaId, listOf())
            storeV2.putMetaAccountSecrets(metaId, newSecrets)

            database.updateSecretsForAccount(metaId, newEthereumKeypair.publicKey)

            val oldAddress = oldEthPubKey?.ethereumAddressFromPublicKey()
            oldAddress?.let { database.updateAccountIdForAsset(it, newEthereumKeypair.publicKey) }
        }
    }

    @SuppressLint("Range")
    private fun getAccounts(database: SupportSQLiteDatabase): List<Long> {
        return database.query("SELECT id FROM meta_accounts").let {
            it.map {
                getLong(getColumnIndex(MetaAccountLocal.Table.Column.ID))
            }
        }
    }

    private fun SupportSQLiteDatabase.updateSecretsForAccount(metaId: Long, ethereumPublicKey: ByteArray) {
        val ethereumAddress = ethereumPublicKey.ethereumAddressFromPublicKey()

        val contentValues = ContentValues().apply {
            put(MetaAccountLocal.Table.Column.ETHEREUM_ADDRESS, ethereumAddress)
            put(MetaAccountLocal.Table.Column.ETHEREUM_PUBKEY, ethereumPublicKey)
        }

        update(MetaAccountLocal.TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE, contentValues, "id=?", arrayOf(metaId.toString()))
    }

    private fun SupportSQLiteDatabase.updateAccountIdForAsset(oldAddress: ByteArray, ethereumPublicKey: ByteArray) {
        val ethereumAddress = ethereumPublicKey.ethereumAddressFromPublicKey()

        val contentValues = ContentValues().apply {
            put("accountId", ethereumAddress)
        }

        update("assets", SQLiteDatabase.CONFLICT_REPLACE, contentValues, "accountId=?", arrayOf(oldAddress))
    }
}
