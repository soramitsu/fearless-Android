package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.utils.enableShowingNewlyAddedTopElements
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.bottomSheet.LockBottomSheetBehavior
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import kotlinx.android.synthetic.main.view_transfer_history.view.placeholder
import kotlinx.android.synthetic.main.view_transfer_history.view.transactionHistoryFilter
import kotlinx.android.synthetic.main.view_transfer_history.view.transactionHistoryList
import kotlinx.android.synthetic.main.view_transfer_history.view.transactionHistoryProgress

typealias ScrollingListener = (position: Int) -> Unit
typealias SlidingStateListener = (Int) -> Unit
typealias TransactionClickListener = (OperationModel) -> Unit

private const val MIN_ALPHA = 0.55 * 255
private const val MAX_ALPHA = 1 * 255

private const val OFFSET_KEY = "OFFSET"
private const val SUPER_STATE = "SUPER_STATE"

class TransferHistorySheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), TransactionHistoryAdapter.Handler {

    private var bottomSheetBehavior: LockBottomSheetBehavior<View>? = null

    private var anchor: View? = null

    private var scrollingListener: ScrollingListener? = null
    private var slidingStateListener: SlidingStateListener? = null
    private var transactionClickListener: TransactionClickListener? = null

    private var adapter: TransactionHistoryAdapter? = null

    private var lastOffset: Float = 0.0F

    private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        anchor?.let {
            bottomSheetBehavior?.peekHeight = parentView.measuredHeight - it.bottom
        }
    }

    init {
        View.inflate(context, R.layout.view_transfer_history, this)

        setBackgroundResource(R.drawable.bg_transfers)

        addScrollListener()

        updateBackgroundAlpha()
    }

    fun provideImageLoader(imageLoader: ImageLoader) {
        adapter = TransactionHistoryAdapter(this, imageLoader)
        transactionHistoryList.adapter = adapter
        transactionHistoryList.setHasFixedSize(true)
        adapterDataObserver = transactionHistoryList.enableShowingNewlyAddedTopElements()
    }

    fun showProgress() {
        placeholder.makeGone()
        transactionHistoryProgress.makeVisible()
        transactionHistoryList.makeGone()

        adapter?.submitList(emptyList())

        bottomSheetBehavior?.isDraggable = false
    }

    fun showPlaceholder(message: String? = null) {
        placeholder.makeVisible()
        message?.let { findViewById<TextView>(R.id.placeholderText)?.text = it }

        transactionHistoryProgress.makeGone()
        transactionHistoryList.makeGone()

        adapter?.submitList(emptyList())

        bottomSheetBehavior?.isDraggable = false
    }

    fun showTransactions(transactions: List<Any>) {
        placeholder.makeGone()
        transactionHistoryProgress.makeGone()
        transactionHistoryList.makeVisible()

        bottomSheetBehavior?.isDraggable = true

        adapter?.submitList(transactions)
    }

    fun setScrollingListener(listener: ScrollingListener) {
        scrollingListener = listener
    }

    fun setSlidingStateListener(listener: SlidingStateListener) {
        slidingStateListener = listener
    }

    fun setTransactionClickListener(listener: TransactionClickListener) {
        transactionClickListener = listener
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()

        return Bundle().apply {
            putParcelable(SUPER_STATE, superState)
            putFloat(OFFSET_KEY, lastOffset)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state[SUPER_STATE] as Parcelable)

            lastOffset = state.getFloat(OFFSET_KEY)
            updateBackgroundAlpha()
        }

        bottomSheetBehavior?.state?.let {
            slidingStateListener?.invoke(it)
        }
    }

    fun setFilterClickListener(clickListener: OnClickListener) {
        transactionHistoryFilter.setOnClickListener(clickListener)
    }

    fun initializeBehavior(anchorView: View) {
        anchor = anchorView

        bottomSheetBehavior = LockBottomSheetBehavior.fromView(this)

        bottomSheetBehavior!!.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                lastOffset = slideOffset

                updateBackgroundAlpha()
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                slidingStateListener?.invoke(newState)
            }
        })

        addLayoutListener()
    }

    override fun onDetachedFromWindow() {
        removeLayoutListener()

        adapterDataObserver?.let { adapter?.unregisterAdapterDataObserver(it) }

        super.onDetachedFromWindow()
    }

    override fun transactionClicked(transactionModel: OperationModel) {
        transactionClickListener?.invoke(transactionModel)
    }

    private fun addScrollListener() {
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

                scrollingListener?.invoke(lastVisiblePosition)
            }
        }

        transactionHistoryList.addOnScrollListener(scrollListener)
    }

    private fun removeLayoutListener() {
        parentView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
    }

    private fun addLayoutListener() {
        parentView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private fun updateBackgroundAlpha() {
        val updatedAlpha = MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * lastOffset

        val color = Color.argb(updatedAlpha.toInt(), 0, 0, 0)

        backgroundTintList = ColorStateList.valueOf(color)
    }

    private val parentView: View
        get() = parent as View
}
