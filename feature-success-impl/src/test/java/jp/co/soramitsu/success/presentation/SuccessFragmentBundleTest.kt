package jp.co.soramitsu.success.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuccessFragmentBundleTest {

    @Test fun bundleCarriesTheOperationAndCustomTexts() {
        val bundle = SuccessFragment.getBundle("0xhash", "chain-id", "Swap accepted", "Swap sent")

        assertEquals("0xhash", bundle.getString(SuccessFragment.KEY_OPERATION_HASH))
        assertEquals("chain-id", bundle.getString(SuccessFragment.KEY_CHAIN_ID))
        assertEquals("Swap accepted", bundle.getString(SuccessFragment.KEY_CUSTOM_MESSAGE))
        assertEquals("Swap sent", bundle.getString(SuccessFragment.KEY_CUSTOM_TITLE))
    }

    @Test fun omittedValuesStayExplicitlyEmpty() {
        val bundle = SuccessFragment.getBundle(operationHash = null, chainId = null, customMessage = null)

        listOf(
            SuccessFragment.KEY_OPERATION_HASH,
            SuccessFragment.KEY_CHAIN_ID,
            SuccessFragment.KEY_CUSTOM_MESSAGE,
            SuccessFragment.KEY_CUSTOM_TITLE
        ).forEach { key ->
            assertTrue(key, bundle.containsKey(key))
            assertNull(key, bundle.getString(key))
        }
    }
}
