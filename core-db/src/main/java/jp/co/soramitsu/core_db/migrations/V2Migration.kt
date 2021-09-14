package jp.co.soramitsu.core_db.migrations

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.ethereumAddress
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.model.WithDerivationPath
import jp.co.soramitsu.core.model.WithSeed
import jp.co.soramitsu.core_db.converters.CryptoTypeConverters
import jp.co.soramitsu.core_db.converters.NetworkTypeConverters
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import kotlinx.coroutines.runBlocking

private class MigratingAccount(
    val address: String,
    val networkType: Node.NetworkType,
    val name: String,
    val cryptoType: CryptoType,
)

class V2Migration(
    private val storeV1: SecretStoreV1,
    private val storeV2: SecretStoreV2,
    private val preferences: Preferences,
    private val gson: Gson,
) : Migration(26, 27) {

    private val networkTypeConverters = NetworkTypeConverters()
    private val cryptoTypeConverters = CryptoTypeConverters()

    /**
     * Migrates from v1 db and secrets model to v2.
     * Note, than old (v1) secrets as well as accounts will not be deleted to be able to restore them in case of critical bug in this migration
     */
    override fun migrate(database: SupportSQLiteDatabase) = runBlocking {
        database.beginTransaction()

        val migratingAccounts = getAccounts(database)

        migratingAccounts.forEachIndexed { index, account ->
            val secrets = storeV1.getSecuritySource(account.address) ?: return@forEachIndexed // Possible RO account, ignore such accounts

            val keypair = secrets.keypair

            val mnemonic = (secrets as? SecuritySource.Specified.Mnemonic)?.let {
                MnemonicCreator.fromWords(secrets.mnemonic)
            }

            val derivationPath = (secrets as? WithDerivationPath)?.let(WithDerivationPath::derivationPath)
            val seed = (secrets as? WithSeed)?.let(WithSeed::seed)

            val ethereumKeypair = mnemonic?.let {
                val ethereumSeed = EthereumSeedFactory.deriveSeed(it.words, password = null).seed // do not use any password during migration

                EthereumKeypairFactory.generate(ethereumSeed, junctions = emptyList())
            }

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = keypair,
                entropy = mnemonic?.entropy,
                seed = seed,
                substrateDerivationPath = derivationPath,
                ethereumKeypair = ethereumKeypair,
                ethereumDerivationPath = null // do not use any derivation path during migration
            )

            val isSelected = index == 0 // mark first account as selected

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keypair.publicKey,
                substrateAccountId = keypair.publicKey.substrateAccountId(),
                substrateCryptoType = account.cryptoType,
                ethereumPublicKey = ethereumKeypair?.publicKey,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddress(),
                name = account.name,
                isSelected = isSelected
            )

            val metaId = insertMetaAccount(metaAccount, database)
            storeV2.putMetaAccountSecrets(metaId, secretsV2)
        }

        database.setTransactionSuccessful()
        database.endTransaction()
    }


    private fun getAccounts(database: SupportSQLiteDatabase): List<MigratingAccount> {
        val cursor = database.query("SELECT * FROM users")

        return cursor.map {
            val address = getString(getColumnIndex("address"))
            val cryptoTypeOrdinal = getInt(getColumnIndex("cryptoType"))
            val networkTypeOrdinal = getInt(getColumnIndex("networkType"))
            val name = getString(getColumnIndex("name"))

            MigratingAccount(
                address = address,
                cryptoType = CryptoType.values()[cryptoTypeOrdinal],
                networkType = networkTypeConverters.toNetworkType(networkTypeOrdinal),
                name = name,
            )
        }
    }

    /**
     * @return id of newly inserted account
     */
    private fun insertMetaAccount(metaAccountLocal: MetaAccountLocal, database: SupportSQLiteDatabase): Long {
        val contentValues = with(metaAccountLocal) {
            ContentValues().apply {
                put(MetaAccountLocal.Table.Column.ETHEREUM_ADDRESS, ethereumAddress)
                put(MetaAccountLocal.Table.Column.ETHEREUM_PUBKEY, ethereumPublicKey)
                put(MetaAccountLocal.Table.Column.NAME, name)
                put(MetaAccountLocal.Table.Column.SUBSTRATE_ACCOUNT_ID, substrateAccountId)
                put(MetaAccountLocal.Table.Column.SUBSTRATE_CRYPTO_TYPE, cryptoTypeConverters.from(substrateCryptoType))
                put(MetaAccountLocal.Table.Column.SUBSTRATE_PUBKEY, substratePublicKey)
                put(MetaAccountLocal.Table.Column.IS_SELECTED, isSelected)
            }
        }

        return database.insert(MetaAccountLocal.TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE, contentValues)
    }

    private inline fun <T> Cursor.map(iteration: Cursor.() -> T): List<T> {
        val result = mutableListOf<T>()

        while (moveToNext()) {
            result.add(iteration())
        }

        return result
    }
}
