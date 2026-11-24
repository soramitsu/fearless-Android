package jp.co.soramitsu.account.impl.presentation.language

import android.view.View
import android.widget.TextView
import jp.co.soramitsu.account.impl.presentation.RecyclerTestHost
import jp.co.soramitsu.account.impl.presentation.language.model.LanguageModel
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguagesAdapterTest {
    private val english = LanguageModel("en", "English", "English")
    private val japanese = LanguageModel("ja", "Japanese", "日本語")

    @Test
    fun `rows show both names, the check follows the selection and a tap reports the language`() {
        val clicked = mutableListOf<LanguageModel>()
        val adapter = LanguagesAdapter(object : LanguagesAdapter.LanguagesItemHandler {
            override fun checkClicked(languageModel: LanguageModel) { clicked += languageModel }
        })
        adapter.submitList(listOf(english, japanese))
        val host = RecyclerTestHost(adapter)

        assertEquals("Japanese", host.itemView(1).findViewById<TextView>(R.id.languageNameTv).text.toString())
        assertEquals("日本語", host.itemView(1).findViewById<TextView>(R.id.languageNativeNameTv).text.toString())
        assertEquals(View.INVISIBLE, host.itemView(0).findViewById<View>(R.id.languageCheck).visibility)

        adapter.updateSelectedLanguage(english)
        host.layout()
        assertEquals(View.VISIBLE, host.itemView(0).findViewById<View>(R.id.languageCheck).visibility)

        adapter.updateSelectedLanguage(japanese)
        host.layout()
        assertEquals(View.INVISIBLE, host.itemView(0).findViewById<View>(R.id.languageCheck).visibility)
        assertEquals(View.VISIBLE, host.itemView(1).findViewById<View>(R.id.languageCheck).visibility)

        host.itemView(1).performClick()
        assertEquals(listOf(japanese), clicked)
    }

    @Test
    fun `languages are the same item by iso and the same content only when equal`() {
        assertTrue(LanguagesDiffCallback.areItemsTheSame(english, english.copy(displayName = "Anglais")))
        assertFalse(LanguagesDiffCallback.areContentsTheSame(english, english.copy(displayName = "Anglais")))
        assertTrue(LanguagesDiffCallback.areContentsTheSame(english, english.copy()))
        assertFalse(LanguagesDiffCallback.areItemsTheSame(english, japanese))
    }
}
