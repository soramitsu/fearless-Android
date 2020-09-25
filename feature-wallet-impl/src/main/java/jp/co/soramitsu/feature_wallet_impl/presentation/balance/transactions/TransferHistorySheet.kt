package jp.co.soramitsu.feature_wallet_impl.presentation.balance.transactions

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.view_transfer_history.view.transactionHistoryList

typealias PageLoadListener = () -> Unit

class TransferHistorySheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var anchor: View? = null
    private var pageLoadListener: PageLoadListener? = null

    private val adapter = TransferHistoryAdapter()

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        anchor?.let {
            bottomSheetBehavior.peekHeight = parentView.measuredHeight - it.measuredHeight - coordinatorParams.topMargin

            removeListener()
        }
    }

    init {
        View.inflate(context, R.layout.view_transfer_history, this)

        setBackgroundResource(R.drawable.bg_transfers)

        transactionHistoryList.adapter = adapter
        transactionHistoryList.setHasFixedSize(true)

        addScrollListener()
    }

    private fun addScrollListener() {
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                val totalItemCount = recyclerView.layoutManager?.itemCount
                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

                if (lastVisiblePosition + 1 == totalItemCount) {
                    pageLoadListener?.invoke()
                }
            }
        }

        transactionHistoryList.addOnScrollListener(scrollListener)
    }

    fun showTransactions(transactions: List<Any>) {
        adapter.submitList(transactions)
    }

    fun anchorTo(newAnchor: View) {
        anchor = newAnchor
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        bottomSheetBehavior = BottomSheetBehavior.from(this)

        removeListener()
    }

    private fun removeListener() {
        parentView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    fun setPageLoadListener(listener: PageLoadListener) {
        pageLoadListener = listener
    }

    private val parentView: View
        get() = parent as View

    private val coordinatorParams: CoordinatorLayout.LayoutParams
        get() = layoutParams as CoordinatorLayout.LayoutParams
}