package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black50
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors

@Composable
fun FullScreenLoading(
    isLoading: Boolean,
    contentAlignment: Alignment = Alignment.TopStart,
    BlurredContent: @Composable () -> Unit
) {
    val restyled = FwRestyle.isEnabled(FwSurface.Loading)
    val blurModifier = if (isLoading && !restyled) Modifier.blur(10.dp) else Modifier
    val inputGate = remember { LoadingInputGate() }
    SideEffect { inputGate.update(isLoading) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { isLoading && it.key != Key.Back && it.key != Key.Escape }
            .pointerInput(Unit) {
                // A persistent ancestor sees gestures that began before the overlay appeared.
                // Retain the revision until all pointers lift, even if loading has already ended.
                awaitPointerEventScope {
                    var gestureRevision: Long? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val revision = gestureRevision ?: inputGate.revision.also { gestureRevision = it }
                        if (inputGate.blocks(revision)) event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) gestureRevision = null
                    }
                }
            },
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = !isLoading }
                .then(if (isLoading) Modifier.clearAndSetSemantics {} else Modifier)
                .then(blurModifier)
        ) {
            BlurredContent()
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (restyled) LocalFwColors.current.scrim.copy(alpha = 0.8f) else black50)
            )
            FearlessProgress(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
@Preview
private fun FullScreenLoadingPreview() {
    FearlessTheme {
        FullScreenLoading(isLoading = true) {
            Column {
                Row {
                    ButtonPreview()
                    ButtonPreview()
                }
                Row {
                    ButtonPreview()
                    ButtonPreview()
                }
            }
        }
    }
}
