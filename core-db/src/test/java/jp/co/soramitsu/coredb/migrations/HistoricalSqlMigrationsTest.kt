package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.coredb.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class HistoricalSqlMigrationsTest {
    @Test
    fun `SQL preview sequence is contiguous and identical to the production graph`() {
        val production = AppDatabase.migrations(
            mock<SecretStoreV1>(), mock<SecretStoreV2>(), mock<EncryptedPreferences>(),
            mock<SubstrateSecretStore>(), mock<EthereumSecretStore>()
        ).filter { it.startVersion in 32..70 }
        assertEquals((32..70).toList(), HISTORICAL_SQL_MIGRATIONS_32_TO_71.map { it.startVersion })
        assertEquals((33..71).toList(), HISTORICAL_SQL_MIGRATIONS_32_TO_71.map { it.endVersion })
        assertEquals(production.size, HISTORICAL_SQL_MIGRATIONS_32_TO_71.size)
        production.zip(HISTORICAL_SQL_MIGRATIONS_32_TO_71).forEach { (actual, preview) ->
            assertSame(actual, preview)
        }
    }
}
