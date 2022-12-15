package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.selectedGreen

enum class CustomSnackbarType(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val color: Color,
    val textColor: Color = grayButtonBackground,
    val extraBottomPadding: Dp = 0.dp
) {
    ADDRESS_COPIED(
        iconRes = R.drawable.ic_copy_24_notifications,
        titleRes = R.string.application_status_view_copied_title,
        descriptionRes = R.string.application_status_view_copied_description,
        color = selectedGreen
    ),
    YOU_OFFLINE(
        iconRes = R.drawable.ic_wifi_off,
        titleRes = R.string.application_status_view_offline_title,
        descriptionRes = R.string.application_status_view_offline_description,
        color = colorAccentDark
    ),
    RECONNECTED(
        iconRes = R.drawable.ic_wifi_on,
        titleRes = R.string.application_status_view_reconnected_title,
        descriptionRes = R.string.application_status_view_reconnected_description,
        color = selectedGreen
    ),
    YOU_OFFLINE_EXTRA_BOTTOM(
        iconRes = R.drawable.ic_wifi_off,
        titleRes = R.string.application_status_view_offline_title,
        descriptionRes = R.string.application_status_view_offline_description,
        color = colorAccentDark,
        extraBottomPadding = 49.dp
    ),
    RECONNECTED_EXTRA_BOTTOM(
        iconRes = R.drawable.ic_wifi_on,
        titleRes = R.string.application_status_view_reconnected_title,
        descriptionRes = R.string.application_status_view_reconnected_description,
        color = selectedGreen,
        extraBottomPadding = 49.dp
    );
}

@Composable
fun TypedSnackbar(type: CustomSnackbarType) {
    BackgroundCornered(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .padding(bottom = type.extraBottomPadding)
            .imePadding(),
        backgroundColor = type.color
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(res = type.iconRes, tint = type.textColor)
            MarginHorizontal(margin = 8.dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                H5(text = stringResource(id = type.titleRes), color = type.textColor)
                B2(text = stringResource(id = type.descriptionRes), color = type.textColor)
            }
        }
    }
}

@Preview
@Composable
fun PreviewCustomSnackbar() {
    FearlessTheme {
        Column {
            TypedSnackbar(CustomSnackbarType.ADDRESS_COPIED)
            TypedSnackbar(CustomSnackbarType.YOU_OFFLINE)
            TypedSnackbar(CustomSnackbarType.RECONNECTED)
        }
    }
}
