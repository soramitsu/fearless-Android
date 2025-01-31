package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.tonconnect.api.model.DappModel
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.common.utils.clickableWithNoIndication

@Composable
fun BannerDApp(dApp: DappModel, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(139.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(white04)
            .clickableWithNoIndication(onClick = onClick)
    ) {
        dApp.background?.let {
            AsyncImage(
                model = getImageRequest(LocalContext.current, it),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Row(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            dApp.icon?.let {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, it),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterVertically)
                )
            }
            MarginHorizontal(margin = 5.dp)
            Column(
                modifier = Modifier
                    .wrapContentSize()
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentWidth(),
                    text = dApp.name.orEmpty(),
                    style = MaterialTheme.customTypography.header3,
                    color = Color.White
                )
                MarginVertical(margin = 8.dp)
                Text(
                    maxLines = 2,
                    modifier = Modifier
                        .wrapContentWidth(),
                    text = dApp.description.orEmpty(),
                    style = MaterialTheme.customTypography.body3.copy(fontSize = 11.sp),
                    color = Color.White
                )
            }
        }
    }
}

@Preview
@Composable
private fun BannerDAppPreview() {
    BannerDApp(
        dApp = DappModel(
            identifier = "",
            chains = listOf(),
            name = "dApp name",
            url = "Dapp url",
            description = "dApp description",
            background = "",
            icon = "",
            metaId = null
        ),
        onClick = {}
    )
}
