package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import jp.co.soramitsu.feature_account_api.domain.model.Node
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SubmittableExtrinsicTest {

    @Test
    fun `should deserialize immortal extrinsic`() {
        val expected = "3502848ad2a3fba73321961cd5d1b8272aa95a21e75dd5b098fb36ed996961ac7b2931015604975bd1ce5ac5d00210216db0944278db674146a08f69257ef45cd1f9f1680800c437195b6181bd3161bdd23fb6bb856ed7427787edef125a692bd512b5880014000400dd0072af4b3b66a01be502555d4ddafb55e8e7df3fb04c836d83255547a8a2ff0700e40b5402"

        val decoded = SubmittableExtrinsic.read(expected)

        val encodedBytes = SubmittableExtrinsic.toByteArray(decoded)
        val encoded = Hex.toHexString(encodedBytes)

        assertEquals(expected, encoded)
    }

    @Test
    fun `should deserialize mortal extrinsic`() {
        val data = "310284fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168003a8eb7f3be70d98d86a9ba66f29d8aae0fea70a820a66f38272044811b21f2e7d5e16c73375a3ac775b98177ff0e125a109f0c58f7d7dc1a507b37879250060ec50238000403340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c6302286bee"

        val decoded = SubmittableExtrinsic.read(data)

        val callIndex = decoded[SubmittableExtrinsic.signedExtrinsic][SignedExtrinsic.call][Call.callIndex]
        val expectedCallIndex = Node.NetworkType.KUSAMA.runtimeConfiguration.pallets.transfers.transferKeepAlive.index

        assertEquals(expectedCallIndex, callIndex)
    }
}