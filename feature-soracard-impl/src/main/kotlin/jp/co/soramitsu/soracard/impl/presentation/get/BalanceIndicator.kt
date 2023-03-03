package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BalanceIndicator(
    modifier: Modifier = Modifier,
    percent: Float,
    label: String
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.x2)),
            progress = percent,
            color = MaterialTheme.customColors.accentPrimary,
            backgroundColor = MaterialTheme.customColors.bgSurfaceVariant
        )

        Text(
            text = label,
            style = MaterialTheme.customTypography.textSBold,
            color = MaterialTheme.customColors.accentPrimary
        )
    }
}

@Composable
@Preview
private fun PreviewBalanceIndicator() {
    BalanceIndicator(
        modifier = Modifier.fillMaxWidth().padding(Dimens.x3),
        percent = 0.75f,
        label = "You have enough balance"
    )
}
