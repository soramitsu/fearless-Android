package jp.co.soramitsu.wallet.api.presentation.view

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.R
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FeeViewTest {
    private fun view() =
        FeeView(ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Soramitsu_Fearless))

    @Test
    fun initialFeeShowsLabelAndLoadingProgress() {
        val view = view()
        assertEquals("Network fee", view.title.text.toString())
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.tableCellValueProgress).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.tableCellContent).visibility)
    }

    @Test
    fun loadedFeeReplacesSpinnerWithCryptoAndFiat() {
        val view = view()
        val fee = mockk<FeeModel> {
            every { displayToken } returns "0.01 ETH"
            every { displayFiat } returns "$20"
        }
        view.setFeeStatus(FeeStatus.Loaded(fee))
        assertEquals("0.01 ETH", view.valuePrimary.text.toString())
        assertEquals("$20", view.findViewById<TextView>(R.id.tableCellValueSecondary).text.toString())
        assertEquals(View.GONE, view.findViewById<View>(R.id.tableCellValueProgress).visibility)
        view.setFeeStatus(null)
        assertEquals("0.01 ETH", view.valuePrimary.text.toString())
        view.setFeeStatus(FeeStatus.Loading)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.tableCellValueProgress).visibility)
    }

    @Test
    fun feeFailureShowsErrorAndHidesFiat() {
        val view = view()
        view.setFeeStatus(FeeStatus.Error)
        assertEquals(view.context.getString(R.string.common_error_general_title), view.valuePrimary.text.toString())
        assertEquals(View.GONE, view.findViewById<View>(R.id.tableCellValueProgress).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.tableCellValueSecondary).visibility)
    }
}
