package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.model.DappConfig
import co.jp.soramitsu.tonconnect.model.DappModel
import jp.co.soramitsu.common.data.network.ton.DappRemote
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.BannerPageIndicator
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.ProgressDialog
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.utils.rememberForeverLazyListState

@Stable
interface DiscoverDappScreenInterface {
    fun onButtonToggleChanged(type: DappListType)
    fun onSeeAllClick(type: String)
    fun onDappClick(dappId: String)
    fun onDappLongClick(dappId: String)
}

@Composable
fun DiscoverDappScreen(
    data: DiscoverDappState,
    callback: DiscoverDappScreenInterface
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MarginVertical(margin = 16.dp)
        MultiToggleButton(
            state = data.multiToggleButtonState,
            onToggleChange = callback::onButtonToggleChanged
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            data.dapps.firstOrNull {
                it.type == "top"
            }?.apps?.takeIf { it.isNotEmpty() }?.let {
                Banners(it, callback)
            }
            val listItems = data.dapps.filter { it.type != "top" }
            if (listItems.isEmpty()) {
                if (data.multiToggleButtonState.currentSelection == DappListType.Connected) {
                    EmptySumimasen()
                } else {
                    ProgressDialog()
                }
            } else {
                listItems.forEach { config ->
                    DappsGroup(
                        data = config,
                        onMoreClick = { config.type?.let { callback.onSeeAllClick(it) } },
                        onDappClick = callback::onDappClick,
                        onDappLongClick = callback::onDappLongClick,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySumimasen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Center
        ) {
            Column(horizontalAlignment = CenterHorizontally) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(100)
                        )
                ) {
                    Icon(
                        painter = painterResource(id = jp.co.soramitsu.common.R.drawable.ic_screen_warning),
                        tint = white,
                        contentDescription = null,
                        modifier = Modifier
                            .testTag("stub_icon")
                            .alpha(0.16f)
                            .padding(top = 10.dp)
                            .align(TopCenter)
                    )
                }
                MarginVertical(margin = 16.dp)
                H3(text = stringResource(id = R.string.common_search_assets_alert_title))
            }
        }
    }

@Composable
fun DappsGroup(
    data: DappConfig,
    onMoreClick: (() -> Unit)?,
    onDappClick: ((String) -> Unit)?,
    onDappLongClick: ((String) -> Unit)?
) {
    BackgroundCornered {
        Column {
            data.type?.let {
                DappGroupHeaderItem(it, onMoreClick)
            }
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                data.apps.forEach {
                    DappItem(it, onDappClick, onDappLongClick)
                }
            }
        }
    }
}

@Composable
private fun DappGroupHeaderItem(title: String, onMoreClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        H5(text = title)
        onMoreClick?.let {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .background(
                        color = white08,
                        shape = CircleShape,
                    )
                    .clickable(onClick = onMoreClick)
                    .padding(horizontal = 8.dp, vertical = 5.5.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.tc_see_all).uppercase(),
                    modifier = Modifier
                        .align(Alignment.CenterVertically),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.customTypography.capsTitle2,
                    color = white,
                )
                Image(
                    res = R.drawable.ic_chevron_right,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(white08)
    )
}

@Composable
fun DappItem(
    dapp: DappModel,
    onDappClick: ((String) -> Unit)?,
    onDappLongClick: ((String) -> Unit)?
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
//            .clickable {
//                onDappClick?.invoke(dapp.identifier)
//            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { /* Called when the gesture starts */ },
                    onDoubleTap = { /* Called on Double Tap */ },
                    onLongPress = {
                        /* Called on Long Press */
                        onDappLongClick?.invoke(dapp.identifier)
                    },
                    onTap = {
                        /* Called on Tap */
                        onDappClick?.invoke(dapp.identifier)
                    }
                )
            }
        .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = dapp.icon?.let { getImageRequest(LocalContext.current, it) },
            contentDescription = null,
            modifier = Modifier
                .testTag("AssetItem_image_${dapp.identifier}")
                .width(40.dp)
                .wrapContentHeight()
                .align(Alignment.CenterVertically)
                .heightIn(max = 70.dp)
        )
        MarginHorizontal(8.dp)
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp)
                .wrapContentHeight()
                .fillMaxWidth()
        ) {
            B0(text = dapp.name.orEmpty())
            MarginVertical(8.dp)
            B2(text = dapp.description.orEmpty())
        }

    }
}

@Preview
@Composable
fun PreviewDappsGroup() {
    DappsGroup(
        data = DappConfig(
            type = "Header",
            apps = listOf(
                DappModel(
                    identifier = "123",
                    chains = listOf("-239"),
                    name = "FW dapp example",
                    url = "https://ton-connect.example.fearless.soramitsu.co.jp",
                    description = "FW dapp example description",
                    background = "https://raw.githubusercontent.com/soramitsu/shared-features-utils/develop-free/icons/dapps/defaultbg.png",
                    icon = "https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icons/FW_icon_288.png"
                )
            )
        ),
        onMoreClick = {},
        onDappClick = {},
        onDappLongClick = {},
    )
}

@Composable
private fun Banners(dapps: List<DappModel>, callback: DiscoverDappScreenInterface) {
    val banners: List<@Composable (() -> Unit)> = dapps.map {
        {
            BannerDApp(
                dApp = it,
                onClick = { callback.onDappClick(it.identifier) }
            )
        }
    }

    val bannersCount = banners.size
    val bannersCarousel: @Composable (() -> Unit)? =
        banners.takeIf { it.isNotEmpty() }?.let {
            {
                val pagerState = rememberPagerState { bannersCount }
                HorizontalPager(
                    modifier = Modifier.fillMaxWidth(),
                    state = pagerState,
                    pageSpacing = 8.dp,
                    pageContent = { page ->
                        banners[page].invoke()
                    }
                )

                if (bannersCount > 1) {
                    BannerPageIndicator(bannersCount, pagerState)
                    MarginVertical(margin = 8.dp)
                }
            }
        }
    if (bannersCarousel != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bannersCarousel.invoke()
        }
    }
}

@Preview
@Composable
private fun PreviewDiscoverDappScreen() {
    val emptyCallback = object : DiscoverDappScreenInterface {
        override fun onButtonToggleChanged(type: DappListType) {}
        override fun onSeeAllClick(type: String) {}
        override fun onDappClick(dappId: String) {}
        override fun onDappLongClick(dappId: String) {}
    }

    val dapps: List<DappConfig> = listOf()

    FearlessAppTheme(true) {
        Column {
            DiscoverDappScreen(
                data = DiscoverDappState(
                    multiToggleButtonState = MultiToggleButtonState(
                        DappListType.Connected,
                        DappListType.entries
                    ),
                    dapps = dapps,
                ),
                callback = emptyCallback
            )
        }
    }
}
