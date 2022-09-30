package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography

private const val MAX_VISIBLE_ICONS = 5

@Composable
fun AssetChainsBadge(
    urls: List<String>,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        val iconsToShow = when {
            urls.size > MAX_VISIBLE_ICONS -> MAX_VISIBLE_ICONS - 1
            else -> urls.size
        }

        urls.subList(0, iconsToShow).map {
            AsyncImage(
                model = getImageRequest(LocalContext.current, it),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .padding(2.dp)
                    .alpha(0.5f)
                    .testTag("AssetChainsBadge_chain_icon")
            )
        }

        val iconsLeft = urls.size - iconsToShow
        if (iconsLeft > 0) {
            Surface(
                modifier = Modifier
                    .height(16.dp)
                    .testTag("AssetChainsBadge_chains_extra")
            ) {
                Text(
                    text = "+$iconsLeft".uppercase(),
                    style = MaterialTheme.customTypography.capsTitle2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .alpha(0.33f)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAssetChainBadge() {
    val list = listOf(
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonbeam.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Statemine.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Rococo.svg"
    )
    FearlessTheme {
        AssetChainsBadge(list)
    }
}
