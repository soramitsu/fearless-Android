package jp.co.soramitsu.account.impl.presentation.view.mnemonic

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MnemonicViewerTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Soramitsu_Fearless)

    @Test
    fun `the viewer lays the numbered words out in two rows inside an inset well`() {
        val viewer = MnemonicViewer(context)
        val words = (1..12).map { MnemonicWordModel(it.toString(), "word$it") }
        viewer.submitList(words)
        viewer.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
        viewer.layout(0, 0, 1080, viewer.measuredHeight)

        val list = viewer.findViewById<RecyclerView>(R.id.mnemonicViewerList)
        assertEquals(6, (list.layoutManager as GridLayoutManager).spanCount)
        assertNotNull(list.background)
        assertTrue(list.paddingTop > 0)
        assertEquals(12, list.adapter!!.itemCount)
        val first = list.findViewHolderForAdapterPosition(0)!!.itemView
        assertEquals("1", first.findViewById<TextView>(R.id.numberTv).text.toString())
        assertEquals("word1", first.findViewById<TextView>(R.id.wordTv).text.toString())
    }

    @Test
    fun `a single word still gets one column and words are compared by number then text`() {
        val viewer = MnemonicViewer(context)
        viewer.submitList(listOf(MnemonicWordModel("1", "solo")))
        assertEquals(1, (viewer.findViewById<RecyclerView>(R.id.mnemonicViewerList).layoutManager as GridLayoutManager).spanCount)

        assertTrue(DiffCallback.areItemsTheSame(MnemonicWordModel("1", "a"), MnemonicWordModel("1", "b")))
        assertFalse(DiffCallback.areContentsTheSame(MnemonicWordModel("1", "a"), MnemonicWordModel("1", "b")))
        assertTrue(DiffCallback.areContentsTheSame(MnemonicWordModel("1", "a"), MnemonicWordModel("2", "a")))
    }
}
