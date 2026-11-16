package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.text.SpannableStringBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EthereumDerivationPathTransformerTest {

    private fun typed(text: String): String {
        EthereumDerivationPathTransformer.onTextChanged(text, 0, 0, 1)
        return SpannableStringBuilder(text).also(EthereumDerivationPathTransformer::afterTextChanged).toString()
    }

    private fun erased(text: String): String {
        EthereumDerivationPathTransformer.onTextChanged(text, 0, 1, 0)
        return SpannableStringBuilder(text).also(EthereumDerivationPathTransformer::afterTextChanged).toString()
    }

    @Test
    fun `typing builds the bip44 separators step by step`() {
        assertEquals("//4", typed("4"))
        assertEquals("//4", typed("/4"))
        assertEquals("//44//4", typed("//444"))
        assertEquals("//44//60//0", typed("//44//600"))
        assertEquals("//44//60//0/0", typed("//44//60//00"))
        assertEquals("//44//60//0/0/0", typed("//44//60//0/00"))
        assertEquals("//44//60//0/0/1", typed("//44//60//0/0/12"))
        assertEquals("//44//60//0/0/1", typed("//44//60//0/0/1"))
        assertEquals("", typed(""))
    }

    @Test
    fun `erasing drops a dangling separator instead of adding one`() {
        assertEquals("//44", erased("//44//"))
        assertEquals("//44//60//0", erased("//44//60//0/"))
        assertEquals("//44", erased("//44"))
        EthereumDerivationPathTransformer.beforeTextChanged("x", 0, 0, 0)
        EthereumDerivationPathTransformer.afterTextChanged(null)
        assertEquals("//4", typed("4"))
    }
}
