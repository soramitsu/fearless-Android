package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.view_toolbar.view.action
import kotlinx.android.synthetic.main.view_toolbar.view.back
import kotlinx.android.synthetic.main.view_toolbar.view.title

enum class BackStyle(@DrawableRes val imageId: Int) {
    ARROW(R.drawable.ic_arrow_back_24dp),
    CROSS(R.drawable.ic_close)
}

class FearlessToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    val actionView: TextView
        get() = action

    val backView: ImageView
        get() = back

    val titleView: TextView
        get() = title

    init {
        View.inflate(context, R.layout.view_toolbar, this)
    }

    fun setTitle(@StringRes title: Int) {
        titleView.setText(title)
    }

    fun setAction(@StringRes label: Int, listener: (View) -> Unit) {
        actionView.visibility = View.VISIBLE

        actionView.setText(label)
        actionView.setOnClickListener(listener)
    }

    fun showBackButton(style: BackStyle = BackStyle.ARROW, listener: (View) -> Unit) {
        backView.visibility = View.VISIBLE

        backView.setImageResource(style.imageId)
        backView.setOnClickListener(listener)
    }
}