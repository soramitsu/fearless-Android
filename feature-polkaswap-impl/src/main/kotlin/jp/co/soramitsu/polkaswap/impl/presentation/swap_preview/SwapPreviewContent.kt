package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

interface SwapPreviewCallbacks

@Composable
fun SwapPreviewContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
    }
}

@Preview
@Composable
fun SwapPreviewContentPreview() {
    SwapPreviewContent()
}
