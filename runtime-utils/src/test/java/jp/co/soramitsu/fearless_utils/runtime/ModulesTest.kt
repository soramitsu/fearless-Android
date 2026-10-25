package jp.co.soramitsu.fearless_utils.runtime

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

private const val ADDRESS = "5CDayXd3cDCWpBkSXVsVfhE5bWKyTZdD3D1XUinR1ezS1sGn"

@RunWith(MockitoJUnitRunner::class)
class ModulesTest {

    @Test
    fun `should create stacking-bonded key`() {
        val expected =
            "0x5f3e4907f716ac89b6347d15ececedca3ed14b45ed20d054f05e37e2542cfe70102af806668257c706c60aeddcff7ecdf122d0299e915f63815cdc06a5fbabaa639588b4b9283d50"

        val bytes = ADDRESS.toAccountId()

        val key = Module.Staking.Bonded.storageKey(bytes)

        assertEquals(expected, key)
    }

    @Test
    fun `should create era key`() {
        val expected = "0x5f3e4907f716ac89b6347d15ececedca487df464e44a534ba6b0cbb32407b587"

        val key = Module.Staking.ActiveEra.storageKey()

        assertEquals(expected, key)
    }

    @Test
    fun `should create ledger key`() {
        val expected =
            "0x5f3e4907f716ac89b6347d15ececedca422adb579f1dbf4f3886c5cfa3bb8cc45d37cb5ab0b196671bc08d32e5013f992cbac888c30f6e7d80b9caf0a18a325fb75f8839281fe74ce9d0528a9e828c3c"

        val publicKeyHex = "2cbac888c30f6e7d80b9caf0a18a325fb75f8839281fe74ce9d0528a9e828c3c"
        val bytes = Hex.decode(publicKeyHex)

        val key = Module.Staking.Ledger.storageKey(bytes)

        assertEquals(expected, key)
    }
}