package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BalanceIndicator(
    modifier: Modifier = Modifier,
    percent: Float,
    label: String,
    loading: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.x2)),
            progress = percent,
            color = colorAccentDark,
            backgroundColor = MaterialTheme.customColors.bgSurfaceVariant
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
                color = colorAccentDark,
                strokeWidth = 3.dp
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.customTypography.textSBold,
                color = colorAccentDark
            )
        }
    }
}

@Composable
@Preview
private fun PreviewBalanceIndicator() {
    Column {
        BalanceIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.x3),
            percent = 0.75f,
            label = "You have enough balance"
        )

        MarginVertical(margin = 8.dp)

        BalanceIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.x3),
            percent = 0.75f,
            label = "You have enough balance",
            loading = true
        )

    }
}
