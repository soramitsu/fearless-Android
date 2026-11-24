package jp.co.soramitsu.common.view

import jp.co.soramitsu.common.view.AccessibilityExposure.Service
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityExposureTest {
    private val services = listOf(
        Service("TalkBack", systemApp = true),
        Service("Screen Helper", systemApp = false),
        Service("Screen Helper", systemApp = false),
        Service("Autofill Assistant", systemApp = false)
    )

    @Test fun `before Android 14 every non-system reader is named once`() {
        assertEquals(listOf("Screen Helper", "Autofill Assistant"), AccessibilityExposure.readersToWarnAbout(33, services))
    }

    @Test fun `Android 14 and later need no warning because sensitive screens are hidden from them`() {
        assertEquals(emptyList<String>(), AccessibilityExposure.readersToWarnAbout(34, services))
    }

    @Test fun `system readers alone raise no warning`() {
        assertEquals(emptyList<String>(), AccessibilityExposure.readersToWarnAbout(30, listOf(Service("TalkBack", true))))
    }
}
