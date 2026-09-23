package jp.co.soramitsu.common.data.network.runtime.binding

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountInfoAssetBalanceTest {

    @Test
    fun `Assets pallet status is preserved with its balance`() {
        val mapped = AssetsAccountInfo(
            balance = BigInteger.valueOf(100),
            status = "Frozen"
        ).toAssetBalance()

        assertEquals(BigInteger.valueOf(100), mapped?.freeInPlanks)
        assertEquals("Frozen", mapped?.status)
    }
}
