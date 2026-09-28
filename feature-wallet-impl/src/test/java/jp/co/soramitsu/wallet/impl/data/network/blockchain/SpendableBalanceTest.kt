package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.AccountData
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.AssetsAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.OrmlTokensAccountData
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendableBalanceTest {

    @Test
    fun `ORML spendable balance subtracts frozen amount and floors at zero`() {
        val partiallyFrozen = OrmlTokensAccountData(
            free = BigInteger.valueOf(100),
            reserved = BigInteger.ZERO,
            frozen = BigInteger.valueOf(40)
        )
        val overFrozen = OrmlTokensAccountData(
            free = BigInteger.TEN,
            reserved = BigInteger.ZERO,
            frozen = BigInteger.valueOf(20)
        )

        assertEquals(BigInteger.valueOf(60), partiallyFrozen.spendableBalance(BigInteger.ZERO))
        assertEquals(BigInteger.ZERO, overFrozen.spendableBalance(BigInteger.ZERO))
    }

    @Test
    fun `system spendable balance subtracts the greater freeze`() {
        val account = AccountInfo(
            nonce = BigInteger.ZERO,
            data = AccountData(
                free = BigInteger.valueOf(100),
                reserved = BigInteger.ZERO,
                miscFrozen = BigInteger.valueOf(25),
                feeFrozen = BigInteger.valueOf(40)
            )
        )

        assertEquals(BigInteger.valueOf(60), account.spendableBalance(BigInteger.ZERO))
    }

    @Test
    fun `equilibrium spendable balance subtracts its lock conservatively`() {
        val currency = BigInteger.valueOf(7)
        val account = EqAccountInfo(
            nonce = BigInteger.ZERO,
            data = EqAccountData(
                lock = BigInteger.valueOf(30),
                balances = mapOf(currency to BigInteger.valueOf(80))
            )
        )

        assertEquals(BigInteger.valueOf(50), account.spendableBalance(currency))
    }

    @Test
    fun `frozen assets pallet account has no spendable balance`() {
        val account = AssetsAccountInfo(
            balance = BigInteger.valueOf(100),
            status = "Frozen"
        )

        assertEquals(BigInteger.ZERO, account.spendableBalance(BigInteger.ZERO))
    }
}
