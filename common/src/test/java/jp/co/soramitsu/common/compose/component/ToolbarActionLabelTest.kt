package jp.co.soramitsu.common.compose.component

import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ToolbarActionLabelTest {
    @Test
    fun navigationAndWalletActionsHaveDistinctNames() {
        assertEquals(R.string.ux_back, toolbarActionLabel(R.drawable.ic_arrow_left_24))
        assertEquals(R.string.ux_back, toolbarActionLabel(R.drawable.ic_arrow_back_24dp))
        assertEquals(R.string.ux_close, toolbarActionLabel(R.drawable.ic_cross_24))
        assertEquals(R.string.ux_close, toolbarActionLabel(R.drawable.ic_cross_32))
        assertEquals(R.string.ux_switch_wallet, toolbarActionLabel(R.drawable.ic_wallet))
        assertNotEquals(toolbarActionLabel(R.drawable.ic_wallet), toolbarActionLabel(R.drawable.ic_arrow_back_24dp))
    }

    @Test
    fun menuActionsHaveLocalizedNamesAndUnknownIconsRemainLabeled() {
        assertEquals(R.string.ux_search, toolbarActionLabel(R.drawable.ic_search))
        assertEquals(R.string.ux_scan, toolbarActionLabel(R.drawable.ic_scan))
        assertEquals(R.string.profile_settings_title, toolbarActionLabel(R.drawable.ic_settings))
        assertEquals(R.string.ux_more, toolbarActionLabel(R.drawable.ic_dots_horizontal_24))
        assertEquals(R.string.ux_more, toolbarActionLabel(-1))
    }
}
