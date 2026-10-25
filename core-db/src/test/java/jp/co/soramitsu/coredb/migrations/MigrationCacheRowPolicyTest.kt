package jp.co.soramitsu.coredb.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationCacheRowPolicyTest {
    @Test fun cacheExemptionsCannotIncludeIdentityOrUserChoiceTables() {
        listOf("meta_accounts", "chain_accounts", "assets", "favorite_chains", "ton_connection").forEach { table ->
            assertThrows(IllegalArgumentException::class.java) {
                TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.copy(uncappedCacheTables = setOf(table))
            }
        }
    }

    @Test fun cacheExemptionsCannotIncludeUnknownOrUnconfiguredTables() {
        assertThrows(IllegalArgumentException::class.java) {
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.copy(uncappedCacheTables = setOf("unrecognized_cache"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.copy(
                maximumRowsByTable = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable - "token_price"
            )
        }
    }

    @Test fun walletIntegrityPolicyOnlyExemptsItsPresentCacheTables() {
        val limits = WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS
        assertTrue(limits.uncappedCacheTables.isNotEmpty())
        assertTrue(limits.uncappedCacheTables.all(limits.allowedForeignKeyTables::contains))
        assertTrue(limits.uncappedCacheTables.all(limits.maximumRowsByTable::containsKey))
        assertTrue(limits.uncappedCacheTables.none { it in setOf("meta_accounts", "chain_accounts", "assets", "favorite_chains", "ton_connection") })
    }

    @Test fun explicitBoundedPolicyAndOlderEdgesKeepTheirLimits() {
        assertTrue(TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.copy(uncappedCacheTables = emptySet()).uncappedCacheTables.isEmpty())
        assertTrue(DB28_V2_FOREIGN_KEY_CHECK_LIMITS.uncappedCacheTables.isEmpty())
        assertTrue(DB31_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.uncappedCacheTables.isEmpty())
        assertEquals(131_072, TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable.getValue("chain_accounts"))
    }
}
