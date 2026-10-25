package jp.co.soramitsu.coredb.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletMigrationRowLimitsTest {

    @Test
    fun minimumAndMaximumOverflowSafeBoundsAreAccepted() {
        val minimum = WalletMigrationRowLimits(
            maxWalletRows = 1,
            maxChainAccountRows = 1
        )
        val maximum = WalletMigrationRowLimits(
            maxWalletRows = Int.MAX_VALUE - 1,
            maxChainAccountRows = Int.MAX_VALUE - 1
        )

        assertEquals(1, minimum.maxWalletRows)
        assertEquals(1, minimum.maxChainAccountRows)
        assertEquals(Int.MAX_VALUE - 1, maximum.maxWalletRows)
        assertEquals(Int.MAX_VALUE - 1, maximum.maxChainAccountRows)
    }

    @Test
    fun nonPositiveAndOverflowingBoundsAreRejectedIndependently() {
        listOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                WalletMigrationRowLimits(
                    maxWalletRows = invalid,
                    maxChainAccountRows = 1
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                WalletMigrationRowLimits(
                    maxWalletRows = 1,
                    maxChainAccountRows = invalid
                )
            }
        }
    }

    @Test
    fun productionBoundsRemainExplicitAndOverflowSafe() {
        val production = WalletMigrationRowLimits.PRODUCTION

        assertEquals(8_192, production.maxWalletRows)
        assertEquals(131_072, production.maxChainAccountRows)
        assertTrue(production.maxWalletRows + 1 > production.maxWalletRows)
        assertTrue(
            production.maxChainAccountRows + 1 >
                production.maxChainAccountRows
        )
    }

    @Test
    fun db31AssetBoundsAreOverflowSafeAndRejectInvalidValues() {
        assertEquals(
            1,
            EthereumDerivationPathMigrationLimits(
                maxAssetRows = 1
            ).maxAssetRows
        )
        assertEquals(
            Int.MAX_VALUE - 1,
            EthereumDerivationPathMigrationLimits(
                maxAssetRows = Int.MAX_VALUE - 1
            ).maxAssetRows
        )

        listOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                EthereumDerivationPathMigrationLimits(
                    maxAssetRows = invalid
                )
            }
        }
    }

    @Test
    fun productionDb31AssetBoundRemainsExplicitAndOverflowSafe() {
        val production = EthereumDerivationPathMigrationLimits.PRODUCTION

        assertEquals(1_048_576, production.maxAssetRows)
        assertTrue(production.maxAssetRows + 1 > production.maxAssetRows)
    }
}
