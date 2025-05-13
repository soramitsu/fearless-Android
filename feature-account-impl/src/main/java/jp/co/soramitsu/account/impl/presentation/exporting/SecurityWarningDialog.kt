package jp.co.soramitsu.account.impl.presentation.exporting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.black2

@Composable
fun SecurityWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    FearlessAppTheme {
        Column(verticalArrangement = Arrangement.Bottom) {
            MarginVertical(margin = 12.dp)
            Column(
                modifier = Modifier.background(backgroundBlack, RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp))
            ) {
                MarginVertical(margin = 2.dp)
                Grip(Modifier.align(Alignment.CenterHorizontally))
                MarginVertical(margin = 8.dp)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    MarginVertical(margin = 24.dp)

                    GradientIcon(
                        iconRes = R.drawable.ic_warning_filled,
                        color = alertYellow,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    MarginVertical(margin = 12.dp)

                    H3(
                        text = stringResource(R.string.account_export_warning_title),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    MarginVertical(margin = 8.dp)

                    Text(
                        text = stringResource(R.string.account_export_warning_message),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = black2
                    )

                    MarginVertical(margin = 24.dp)

                    AccentButton(
                        text = stringResource(R.string.common_proceed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        onClick = onConfirm
                    )

                    MarginVertical(margin = 12.dp)

                    GrayButton(
                        text = stringResource(R.string.common_cancel),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        onClick = onDismiss
                    )

                    MarginVertical(margin = 12.dp)
                }
            }
        }

    }
}

@Preview(
    name = "Security Warning Dialog",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun SecurityWarningDialogPreview() {
    FearlessAppTheme {
        SecurityWarningDialog(
            onConfirm = {},
            onDismiss = {}
        )
    }
}