package jp.co.soramitsu.common.compose.component

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.utils.clickableWithNoIndication

data class MainToolbarViewState(
    val title: String,
    val homeIconState: ToolbarHomeIconState = ToolbarHomeIconState.Navigation(R.drawable.ic_wallet),
    val selectorViewState: ChainSelectorViewState
)

data class MainToolbarViewStateWithFilters(
    val title: String?,
    val homeIconState: ToolbarHomeIconState = ToolbarHomeIconState.Navigation(R.drawable.ic_wallet),
    val selectorViewState: ChainSelectorViewStateWithFilters?
)

sealed interface ToolbarHomeIconState{
    data class Wallet(
        val walletIcon: Drawable,
        val score: Int? = null,
    ): ToolbarHomeIconState
    data class Navigation(
        @DrawableRes val navigationIcon: Int,
        val tint: Color = Color.Unspecified
    ): ToolbarHomeIconState
}

data class MenuIconItem(
    @DrawableRes val icon: Int,
    val onClick: () -> Unit
)

data class ToolbarViewState(
    val title: String,
    @DrawableRes val navigationIcon: Int? = null,
    val menuItems: List<MenuIconItem>? = null
)

fun ToolbarViewState(
    title: String,
    @DrawableRes navigationIcon: Int? = null,
    vararg menuItems: MenuIconItem
): ToolbarViewState{
    return ToolbarViewState(title, navigationIcon, menuItems.asList())
}

@Composable
fun MainToolbar(
    state: MainToolbarViewState,
    onChangeChainClick: (() -> Unit)?,
    onNavigationClick: () -> Unit = {},
    menuItems: List<MenuIconItem>? = null,
    modifier: Modifier = Modifier
) {
    val paddingTitleEnd = menuItems.orEmpty().size * (32 /* icon size */ + 8 /* padding */)
    val paddingTitleStart = 40 /* icon size */ + 8 /* padding */
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(CenterStart)
        ) {
            ToolbarHomeIcon(
                state = state.homeIconState,
                onClick = onNavigationClick
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = paddingTitleStart.dp, end = paddingTitleEnd.dp)
                .align(Alignment.Center),
            horizontalAlignment = CenterHorizontally
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.customTypography.header4,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

            MarginVertical(margin = 4.dp)

            ChainSelector(
                selectorViewState = state.selectorViewState,
                onChangeChainClick = onChangeChainClick
            )
        }
        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = spacedBy(8.dp, End),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            menuItems?.forEach { menuItem ->
                IconButton(
                    onClick = menuItem.onClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(backgroundBlurColor)
                        .size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = menuItem.icon),
                        tint = white,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun MainToolbar(
    state: MainToolbarViewStateWithFilters,
    onChangeChainClick: () -> Unit,
    onNavigationClick: () -> Unit = {},
    onScoreClick: () -> Unit,
    menuItems: List<MenuIconItem>? = null,
    modifier: Modifier = Modifier
) {
    val paddingTitleEnd = menuItems.orEmpty().size * (32 /* icon size */ + 8 /* padding */)
    val paddingTitleStart = 40 /* icon size */ + 8 /* padding */
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(CenterStart)
        ) {
            ToolbarHomeIcon(
                state = state.homeIconState,
                onClick = onNavigationClick,
                onScoreClick = onScoreClick
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = paddingTitleStart.dp, end = paddingTitleEnd.dp)
                .align(Alignment.Center),
            horizontalAlignment = CenterHorizontally
        ) {
            if (state.title != null) {
                Text(
                    text = state.title,
                    style = MaterialTheme.customTypography.header4,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            } else {
                Shimmer(Modifier.height(14.dp))
            }

            MarginVertical(margin = 4.dp)

            if (state.selectorViewState != null) {
                ChainSelector(
                    selectorViewState = state.selectorViewState,
                    onChangeChainClick = onChangeChainClick
                )
            } else {
                Shimmer(
                    Modifier
                        .height(12.dp)
                        .padding(horizontal = 20.dp))
            }
        }
        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = spacedBy(8.dp, End),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            menuItems?.forEach { menuItem ->
                IconButton(
                    onClick = menuItem.onClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(backgroundBlurColor)
                        .size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = menuItem.icon),
                        tint = white,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun MainToolbarShimmer(
    homeIconState: ToolbarHomeIconState? = null,
    menuItems: List<MenuIconItem>? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f)
        ) {
            (homeIconState as? ToolbarHomeIconState.Navigation)?.let {
                IconButton(
                    painter = painterResource(id = it.navigationIcon),
                    tint = Color.Unspecified,
                    onClick = {}
                )

            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f),
            horizontalAlignment = CenterHorizontally
        ) {
            Shimmer(Modifier.height(14.dp))
            MarginVertical(margin = 12.dp)
            Shimmer(
                Modifier
                    .height(12.dp)
                    .padding(horizontal = 20.dp)
            )
        }
        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = spacedBy(8.dp, End),
            modifier = Modifier.weight(1f)
        ) {
            menuItems?.forEach { menuItem ->
                IconButton(
                    onClick = menuItem.onClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(backgroundBlurColor)
                        .size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = menuItem.icon),
                        tint = white,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun ToolbarHomeIcon(state: ToolbarHomeIconState, onClick: () -> Unit, onScoreClick: () -> Unit = {}) {
    when (state) {
        is ToolbarHomeIconState.Navigation -> {
            IconButton(
                painter = painterResource(id = state.navigationIcon),
                tint = state.tint,
                onClick = onClick
            )
        }

        is ToolbarHomeIconState.Wallet -> {
            Column(horizontalAlignment = CenterHorizontally) {
                IconButton(
                    painter = rememberAsyncImagePainter(model = state.walletIcon),
                    onClick = onClick
                )
                MarginVertical(margin = 6.dp)

                state.score?.let {
                    Box(modifier = Modifier.clickableWithNoIndication { onScoreClick() }) {
                        ScoreStar(score = it)
                    }
                }
            }
        }
    }
}

@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    painter: Painter,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundBlurColor)
            .size(40.dp)
    ) {
        Icon(
            painter = painter,
            tint = tint,
            contentDescription = null
        )
    }
}

@Composable
fun Toolbar(state: ToolbarViewState, modifier: Modifier = Modifier, onNavigationClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        state.navigationIcon?.let { navIcon ->
            Box(
                contentAlignment = CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                ToolbarHomeIcon(
                    state = ToolbarHomeIconState.Navigation(navigationIcon = navIcon),
                    onClick = onNavigationClick
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(4f),
            horizontalAlignment = CenterHorizontally
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.customTypography.header4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = spacedBy(8.dp, End),
            modifier = Modifier.weight(1f)
        ) {
            state.menuItems?.forEach { menuItem ->
                IconButton(
                    onClick = menuItem.onClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(backgroundBlurColor)
                        .size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = menuItem.icon),
                        tint = white,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MainToolbarPreview() {
    FearlessAppTheme {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(16.dp)
        ) {
            MainToolbarShimmer(
                homeIconState = ToolbarHomeIconState.Navigation(navigationIcon = R.drawable.ic_wallet),
                menuItems = listOf(
                    MenuIconItem(icon = R.drawable.ic_scan, {}),
                    MenuIconItem(icon = R.drawable.ic_search, {})
                )
            )

            MainToolbar(
                state = MainToolbarViewState(
                    title = "Fearless wallet very long wallet name",
                    homeIconState = ToolbarHomeIconState.Navigation(navigationIcon = R.drawable.ic_wallet),
                    selectorViewState = ChainSelectorViewState(
                        selectedChainId = "id",
                        selectedChainName = "Crust shadow parachain",
                        selectedChainStatusColor = colorAccent
                    )
                ),
                menuItems = listOf(
                    MenuIconItem(icon = R.drawable.ic_scan, {}),
                    MenuIconItem(icon = R.drawable.ic_search, {})
                ),
                onChangeChainClick = {},
                onNavigationClick = {}
            )
            MarginVertical(margin = 16.dp)
            Toolbar(
                state = ToolbarViewState(
                    "Pool staking",
                    R.drawable.ic_arrow_back_24dp,
                    listOf(
                        MenuIconItem(icon = R.drawable.ic_dots_horizontal_24, {})
                    )
                ),
                onNavigationClick = {}
            )
            MarginVertical(margin = 16.dp)
            Toolbar(
                state = ToolbarViewState(
                    "Pool staking",
                    null,
                    listOf(
                        MenuIconItem(icon = R.drawable.ic_cross_24, {})
                    )
                ),
                onNavigationClick = {}
            )
        }
    }
}
