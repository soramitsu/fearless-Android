package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.blurColorLight
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white16

data class AssetSelectorState(
    val title: String,
    val iconUrl: String,
    val balance: String,
    val badge: String? = null
)

@Composable
fun AssetSelector(
    state: AssetSelectorState,
    onClick: () -> Unit = {}
) {
    BackgroundCornered(
        backgroundColor = blurColorLight,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            MarginHorizontal(16.dp)
            AsyncImage(
                model = getImageRequest(LocalContext.current, state.iconUrl),
                contentDescription = state.title,
                modifier = Modifier
                    .size(42.dp)
                    .padding(horizontal = 8.dp)
                    .align(Alignment.CenterVertically)
            )
            Column(
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Row {
                    Text(
                        text = state.title,
                        style = MaterialTheme.customTypography.body1
                    )
                    MarginHorizontal(margin = 8.dp)
                    state.badge?.let {
                        Badge(it)
                    }
                }

                Text(
                    text = state.balance,
                    style = MaterialTheme.customTypography.body2,
                    modifier = Modifier.alpha(0.64f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Image(res = R.drawable.ic_dots_horizontal_24, modifier = Modifier.align(Alignment.CenterVertically))
            MarginHorizontal(margin = 16.dp)
        }
    }
}

@Composable
fun Badge(text: String) {
    Row(modifier = Modifier.background(color = white16, shape = RoundedCornerShape(size = 3.dp))) {
        MarginHorizontal(margin = 6.dp)
        Text(
            text = text.uppercase(),
            style = MaterialTheme.customTypography.capsTitle2
        )
        MarginHorizontal(margin = 6.dp)
    }
}

@Composable
@Preview
private fun AssetSelectorPreview() {
    FearlessTheme {
        AssetSelector(
            state = AssetSelectorState(
                title = "Kusama",
                iconUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
                balance = "300 KSM",
                "Pool"
            )
        )
    }
}
