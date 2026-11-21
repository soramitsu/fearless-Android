package jp.co.soramitsu.account.impl.presentation

import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.feature_account_impl.R

/** A laid-out RecyclerView for adapter tests (d-vmviews): every item is bound and reachable by position. */
class RecyclerTestHost(adapter: RecyclerView.Adapter<*>) {
    val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Soramitsu_Fearless)
    val recycler = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        itemAnimator = null
        this.adapter = adapter
    }

    init {
        layout()
    }

    fun layout() {
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, WIDTH, HEIGHT)
    }

    fun itemView(position: Int): View = recycler.findViewHolderForAdapterPosition(position)!!.itemView

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 20_000
    }
}
