package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.graphics.Rect
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.backgroundBlack


interface QuickInput {
    val label: String
    val value: Double
}

enum class QuickAmountInput(
    override val label: String,
    override val value: Double
) : QuickInput {
//    MAX("MAX", 1.0),
    P75("75%", 0.75),
    P50("50%", 0.5),
    P25("25%", 0.25)
}

@Composable
fun QuickInput(
    modifier: Modifier = Modifier,
    values: Array<out QuickInput> = QuickAmountInput.entries.toTypedArray(),
    onQuickAmountInput: (amount: Double) -> Unit = {},
    onDoneClick: () -> Unit = {}
) {
    var isKeyboardVisible by remember { mutableStateOf(false) }
    val rootView = LocalView.current
    val localContext = LocalContext.current

    DisposableEffect(rootView) {
        val rect = Rect()
        rootView.getWindowVisibleDisplayFrame(rect)
        var previousHeight: Int = rect.height()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val newHeight: Int = rootView.height

            if (previousHeight < newHeight) {
                // content decreased - keyboard is shown? check input method
                isKeyboardVisible = false
            } else if(previousHeight > newHeight) {
                val isAcceptingText = (localContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).isAcceptingText
                if (isAcceptingText) {
                    // yeah - this is a keyboard
                    isKeyboardVisible = true
                }
            }


            previousHeight = newHeight
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .background(color = backgroundBlack.copy(alpha = 0.75f))
            .height(44.dp)
            .padding(horizontal = 10.dp)
    ) {
        values.map {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable {
                        onQuickAmountInput(it.value)
                    }
            ) {
                B1(
                    text = it.label,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable {
                    keyboardController?.hide()
                    onDoneClick()
                }
        ) {
            H5(
                text = stringResource(id = R.string.common_done),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

private enum class TestQuickInput(
    override val label: String,
    override val value: Double
) : QuickInput {
    MAX("MAX", 1.0),
    P75("75%", 0.75),
    P50("50%", 0.5),
    P25("25%", 0.25)
}

@Composable
@Preview
private fun QuickInputPreview() {
    QuickInput(
        values = TestQuickInput.values()
    )
}
