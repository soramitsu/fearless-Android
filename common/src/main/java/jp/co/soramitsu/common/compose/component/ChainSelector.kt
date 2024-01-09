package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.ui_core.modifier.applyIf

data class ChainSelectorViewState(
    val selectedChainName: String? = null,
    val selectedChainId: String? = null,
    val selectedChainStatusColor: Color = colorAccent
)

data class ChainSelectorViewStateWithFilters(
    val selectedChainName: String? = null,
    val selectedChainId: String? = null,
    val selectedChainImageUrl: String? = null,
    val filterApplied: Filter = Filter.All
) {
    enum class Filter {
        All, Popular, Favorite
    }
}

@Composable
fun ChainSelector(
    selectorViewState: ChainSelectorViewState,
    onChangeChainClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBlurColor)
            .applyIf(onChangeChainClick != null) {
                clickable(
                    role = Role.Button,
                    onClick = onChangeChainClick!!
                )
            }
    ) {
        Box(Modifier.padding(8.dp)) {
            Canvas(
                modifier = Modifier
                    .size(6.dp)
            ) {
                drawCircle(color = selectorViewState.selectedChainStatusColor)
            }
        }
        Text(
            text = selectorViewState.selectedChainName ?: stringResource(R.string.chain_selection_all_networks),
            style = MaterialTheme.customTypography.body1,
            maxLines = 1
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_down),
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
            tint = white
        )
    }
}

@Composable
fun ChainSelector(
    selectorViewState: ChainSelectorViewStateWithFilters,
    onChangeChainClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBlurColor)
            .clickable(
                role = Role.Button,
                onClick = onChangeChainClick
            )
    ) {
        Box(
            Modifier
                .padding(8.dp)
                .size(16.dp)) {
            when {
                selectorViewState.selectedChainImageUrl != null ->
                    AsyncImage(
                        model = getImageRequest(
                            LocalContext.current,
                            selectorViewState.selectedChainImageUrl
                        ),
                        contentDescription = null,
                    )

                selectorViewState.filterApplied === ChainSelectorViewStateWithFilters.Filter.All ->
                    Image(
                        res = R.drawable.ic_all_chains,
                        tint = white,
                        modifier = Modifier.size(24.dp)
                    )

                selectorViewState.filterApplied === ChainSelectorViewStateWithFilters.Filter.Popular ->
                    Image(
                        res = R.drawable.ic_popular_chains,
                        tint = white,
                        modifier = Modifier.size(24.dp)
                    )

                selectorViewState.filterApplied === ChainSelectorViewStateWithFilters.Filter.Favorite ->
                    Image(
                        res = R.drawable.ic_favorite_enabled,
                        tint = white,
                        modifier = Modifier.size(24.dp)
                    )
            }
        }

        val selectedChainTitle = selectorViewState.selectedChainName ?:
        when(selectorViewState.filterApplied) {
            ChainSelectorViewStateWithFilters.Filter.All ->
                stringResource(R.string.chain_selection_all_networks)

            ChainSelectorViewStateWithFilters.Filter.Popular ->
                stringResource(id = R.string.network_management_popular)

            ChainSelectorViewStateWithFilters.Filter.Favorite ->
                stringResource(id = R.string.network_managment_favourite)
        }

        Text(
            text = selectedChainTitle,
            style = MaterialTheme.customTypography.body1,
            maxLines = 1
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_down),
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
            tint = white
        )
    }
}

@Preview
@Composable
private fun ChainSelectorPreview() {
    ChainSelector(
        selectorViewState = ChainSelectorViewState(
            selectedChainId = "id",
            selectedChainName = "Kusama",
            selectedChainStatusColor = colorAccent
        ),
        onChangeChainClick = {}
    )
}
