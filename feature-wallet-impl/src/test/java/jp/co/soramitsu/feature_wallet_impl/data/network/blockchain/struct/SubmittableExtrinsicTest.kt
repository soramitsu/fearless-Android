package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.spongycastle.util.encoders.Hex

@RunWith(MockitoJUnitRunner::class)
class SubmittableExtrinsicTest {

    @Test
    fun `should deserialize and serialize extrinsic`() {
        val expected = "3502848ad2a3fba73321961cd5d1b8272aa95a21e75dd5b098fb36ed996961ac7b2931015604975bd1ce5ac5d00210216db0944278db674146a08f69257ef45cd1f9f1680800c437195b6181bd3161bdd23fb6bb856ed7427787edef125a692bd512b5880014000400dd0072af4b3b66a01be502555d4ddafb55e8e7df3fb04c836d83255547a8a2ff0700e40b5402"

        val decoded = SubmittableExtrinsic.read(expected)

        val encodedBytes = SubmittableExtrinsic.toByteArray(decoded)
        val encoded = Hex.toHexString(encodedBytes)

        assertEquals(expected, encoded)
    }
}