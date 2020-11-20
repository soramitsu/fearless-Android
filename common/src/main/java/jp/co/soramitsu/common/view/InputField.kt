package jp.co.soramitsu.common.view

import android.content.Context
import android.content.ContextWrapper
import android.text.InputType
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.shape.getCutCornersStateDrawable
import kotlinx.android.synthetic.main.button_glassy.view.buttonGlassyContent

class InputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.style.Widget_Fearless_Input_Primary_External
) : TextInputLayout(context, attrs, defStyle) {

    val content: EditText
        get() = editText!!

    init {
        View.inflate(context, R.layout.view_input_field, this)

        content.background = context.getCutCornersStateDrawable()

        attrs?.let(::applyAttributes)
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.InputField)

        val inputType = typedArray.getInt(R.styleable.InputField_android_inputType, InputType.TYPE_CLASS_TEXT)
        content.inputType = inputType

        val text = typedArray.getString(R.styleable.InputField_android_text)
        content.setText(text)

        typedArray.recycle()
    }
}