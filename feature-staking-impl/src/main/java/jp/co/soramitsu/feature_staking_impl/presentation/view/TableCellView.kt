package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_table_cell.view.tableCellContent
import kotlinx.android.synthetic.main.view_table_cell.view.tableCellTitle
import kotlinx.android.synthetic.main.view_table_cell.view.tableCellValuePrimary
import kotlinx.android.synthetic.main.view_table_cell.view.tableCellValueProgress
import kotlinx.android.synthetic.main.view_table_cell.view.tableCellValueSecondary

open class TableCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    val title: TextView
        get() = tableCellTitle

    private val valuePrimary: TextView
        get() = tableCellValuePrimary

    private val valueSecondary: TextView
        get() = tableCellValueSecondary

    private val valueProgress: ProgressBar
        get() = tableCellValueProgress

    private val contentGroup: Group
        get() = tableCellContent

    init {
        View.inflate(context, R.layout.view_table_cell, this)

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TableCellView)

        val title = typedArray.getString(R.styleable.TableCellView_title)
        setTitle(title)

        typedArray.recycle()
    }

    fun setTitle(titleRes: Int) {
        tableCellTitle.setText(titleRes)
    }

    fun setTitle(title: String?) {
        tableCellTitle.text = title
    }

    fun showProgress() {
        contentGroup.makeGone()
        valueProgress.makeVisible()
    }

    fun showValue(primary: String, secondary: String? = null) {
        contentGroup.makeVisible()

        valuePrimary.text = primary
        valueSecondary.setTextOrHide(secondary)

        valueProgress.makeGone()
    }
}
