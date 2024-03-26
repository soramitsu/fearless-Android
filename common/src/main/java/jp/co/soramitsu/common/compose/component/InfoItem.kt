package jp.co.soramitsu.common.compose.component

import android.graphics.Color
import android.text.TextUtils
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50

data class InfoItemViewState(
    val title: String?,
    val subtitle: String?,
    val imageUrl: String? = null,
    val placeholderIcon: Int? = null,
    val singleLine: Boolean = false
) {
    companion object {
        val default = InfoItemViewState(null, null, null, R.drawable.ic_dapp_connection)
    }
}

@Composable
fun InfoItem(state: InfoItemViewState) {
    BackgroundCorneredWithBorder(
        modifier = Modifier.fillMaxWidth()
    ) {
        InfoItemContent(modifier = Modifier.padding(12.dp), state)
    }
}

@Composable
fun InfoItemContent(modifier: Modifier = Modifier, state: InfoItemViewState) {
    Row(
        modifier.fillMaxWidth()
    ) {
        if (state.imageUrl.isNullOrBlank()) {
            state.placeholderIcon?.let {
                Image(
                    res = state.placeholderIcon,
                    modifier = Modifier
                        .align(CenterVertically)
                )
                MarginHorizontal(margin = 8.dp)
            }
        } else {
            AsyncImage(
                model = getImageRequest(LocalContext.current, state.imageUrl),
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .testTag("ConnectionItem_image_${state.title}")
                    .align(CenterVertically)
                    .size(24.dp)
            )
            MarginHorizontal(margin = 8.dp)
        }
        Column(
            modifier = Modifier
                .align(CenterVertically)
                .weight(1f)
        ) {
            state.title?.let { H5(text = it, color = white50) }
            state.subtitle?.let {
                if (state.singleLine) {
                    B1EllipsizeMiddle(text = state.subtitle)
                } else {
                    B1(text = it, color = white)
                }
            }
        }
    }
}

@Preview
@Composable
private fun InfoItemPreview() {
    val state = InfoItemViewState(
        title = "Info item title",
        singleLine = true,
        subtitle = "My account account account account account account account account"
    )
    FearlessTheme {
        InfoItem(state)
    }
}
