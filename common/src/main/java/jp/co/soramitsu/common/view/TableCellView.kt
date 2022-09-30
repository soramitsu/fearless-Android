package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.utils.setVisible

open class TableCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    val title: TextView
        get() = findViewById(R.id.tableCellTitle)

    private val valuePrimary: TextView
        get() = findViewById(R.id.tableCellValuePrimary)

    private val valueSecondary: TextView
        get() = findViewById(R.id.tableCellValueSecondary)

    private val valueProgress: ProgressBar
        get() = findViewById(R.id.tableCellValueProgress)

    private val contentGroup: Group
        get() = findViewById(R.id.tableCellContent)

    private val tableCellTitle: TextView
        get() = findViewById(R.id.tableCellTitle)

    private val tableCellValueDivider: View
        get() = findViewById(R.id.tableCellValueDivider)

    init {
        View.inflate(context, R.layout.view_table_cell, this)

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TableCellView)

        val title = typedArray.getString(R.styleable.TableCellView_title)
        setTitle(title)

        val dividerVisible = typedArray.getBoolean(R.styleable.TableCellView_dividerVisible, true)
        setDividerVisible(dividerVisible)

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

    fun setDividerVisible(visible: Boolean) {
        tableCellValueDivider.setVisible(visible)
    }

    fun showValue(primary: String, secondary: String? = null) {
        contentGroup.makeVisible()

        valuePrimary.text = primary
        valueSecondary.setTextOrHide(secondary)

        valueProgress.makeGone()
    }

    fun setValueColorRes(@ColorRes colorId: Int) {
        valuePrimary.setTextColorRes(colorId)
    }
}
