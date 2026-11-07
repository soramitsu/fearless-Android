package jp.co.soramitsu.common.view

import android.view.View
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class SensitiveContentTest {
    @Test fun `a sensitive view drops touches through other apps' windows`() {
        val view = mock(View::class.java)
        view.protectSensitiveContent()
        verify(view).filterTouchesWhenObscured = true
    }
}
