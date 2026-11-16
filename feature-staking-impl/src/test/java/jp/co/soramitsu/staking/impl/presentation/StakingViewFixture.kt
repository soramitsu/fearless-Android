package jp.co.soramitsu.staking.impl.presentation

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider

abstract class StakingViewFixture {
    protected val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)

    protected fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
}
