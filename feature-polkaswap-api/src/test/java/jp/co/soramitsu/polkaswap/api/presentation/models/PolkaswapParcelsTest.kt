package jp.co.soramitsu.polkaswap.api.presentation.models

import android.os.Parcel
import android.os.Parcelable
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
class PolkaswapParcelsTest {

    @Suppress("DEPRECATION")
    private fun <T : Parcelable> roundTrip(value: T): T {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(value, 0)
            parcel.setDataPosition(0)
            @Suppress("UNCHECKED_CAST")
            return parcel.readParcelable<T>(value.javaClass.classLoader) as T
        } finally {
            parcel.recycle()
        }
    }

    private val fee = SwapDetailsViewState.NetworkFee("XOR", "0.0007 XOR", "$0.32")

    @Test fun quoteSurvivesTheTripToThePreviewScreen() {
        val quote = SwapDetailsViewState(
            "0x01", "0x02", "XOR", "VAL", null, null, "1 XOR", "2 VAL", "1.99 VAL", "$1", "0.5", "2", "Minimum received", null
        )

        val restored = roundTrip(quote)

        assertNotSame(quote, restored)
        assertEquals(quote, restored)
    }

    @Test fun exactAmountsAndSettingsSurviveTheTrip() {
        val details = SwapDetailsParcelModel(BigInteger("1000000000000000000"), Market.TBC, WithDesired.OUTPUT, 1, BigInteger("990"), fee)
        val noLimit = details.copy(minMax = null, selectedMarket = Market.SMART, desired = WithDesired.INPUT)
        val settings = TransactionSettingsModel(Market.XYK, 0.5)

        assertEquals(details, roundTrip(details))
        assertEquals(noLimit, roundTrip(noLimit))
        assertEquals(settings, roundTrip(settings))
        assertEquals(fee, roundTrip(fee))
    }
}
