package jp.co.soramitsu.account.impl.presentation.recovery

import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryInputPrivacyInstrumentedTest {
    @Test fun directlySavingFragmentRootCannotPersistPhrasePasswordOrDynamicallyAddedInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = FrameLayout(context)
            val nested = FrameLayout(context)
            root.addView(nested)
            val phrase = EditText(context).apply { id = 101; setText("public test phrase") }
            nested.addView(phrase)
            val control = SparseArray<Parcelable>()
            root.saveHierarchyState(control)
            assertTrue(control.size() > 0)
            root.excludeRecoveryInputFromPersistence()
            val dynamic = EditText(context).apply { id = 102; setText("public test password") }
            dynamic.excludeRecoveryInputFromPersistence()
            nested.addView(dynamic)
            val saved = SparseArray<Parcelable>()
            root.saveHierarchyState(saved)
            assertEquals(0, saved.size())
            for (input in listOf(phrase, dynamic)) {
                assertFalse(input.isSaveEnabled)
                assertFalse(input.isSaveFromParentEnabled)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, input.importantForAutofill)
                assertTrue(input.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            }
        }
    }
}
