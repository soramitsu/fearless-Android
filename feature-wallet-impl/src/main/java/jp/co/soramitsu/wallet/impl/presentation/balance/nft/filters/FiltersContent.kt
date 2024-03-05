package jp.co.soramitsu.wallet.impl.presentation.balance.nft.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.H4Bold
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.nft.domain.models.NFTFilter


val switchColors = object : SwitchColors {
    @Composable
    override fun thumbColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(white)
    }

    @Composable
    override fun trackColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(transparent)
    }
}

@Composable
fun FiltersContent(
    state: Map<NFTFilter, Boolean>,
    onSelectClick: (NFTFilter, Boolean) -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(42.dp)
        ) {
            H4(
                text = "Hide NFTs",
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_close_16_white_circle),
                tint = white,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onCloseClick)
                    .align(Alignment.TopEnd)
            )
        }

        MarginVertical(
            margin = 8.dp
        )

        state.forEach { (filter, checked) ->
            val trackColor = if (checked) {
                colorAccent
            } else black3

            Row(
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth()
            ) {
                H4Bold(
                    text = filter.name,
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    colors = switchColors,
                    checked = checked,
                    onCheckedChange = { onSelectClick(filter, it) },
                    modifier = Modifier
                        .background(
                            color = trackColor,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(3.dp)
                        .height(20.dp)
                        .width(35.dp)
                )
            }
        }
    }
}

//@Preview
//@Composable
//private fun FiltersContentPreview() {
//    FearlessAppTheme {
//        FiltersContent(NftFilterModel(NFTFilter.values().associateWith { true }), {}, {})
//    }
//}
