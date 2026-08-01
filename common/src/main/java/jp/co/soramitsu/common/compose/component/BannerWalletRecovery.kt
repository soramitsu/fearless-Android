package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BannerWalletRecovery() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(1.dp, colorAccent),
                shape = RoundedCornerShape(15.dp)
            )
            .background(white04, RoundedCornerShape(15.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text(
            text = stringResource(R.string.wallet_recovery_required_title),
            style = MaterialTheme.customTypography.headline2,
            color = Color.White
        )
        MarginVertical(margin = 8.dp)
        Text(
            text = stringResource(R.string.wallet_recovery_required_message),
            style = MaterialTheme.customTypography.paragraphXS,
            color = Color.White
        )
    }
}
