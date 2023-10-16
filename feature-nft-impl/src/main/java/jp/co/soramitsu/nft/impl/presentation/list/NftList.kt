package jp.co.soramitsu.nft.impl.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.CapsTitle
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H4Bold
import jp.co.soramitsu.common.compose.component.H5Bold
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.RoundedRect
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black50
import jp.co.soramitsu.common.compose.theme.shimmerColor
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.compose.theme.white50


@Composable
fun NftList(state: NftScreenState.ListState, appearanceType: NftAppearanceType) {
    when (state) {
        is NftScreenState.ListState.Content -> NftList(state.items, appearanceType)
        NftScreenState.ListState.Empty -> NftEmptyState()
        NftScreenState.ListState.Loading -> NftListShimmers(appearanceType)
    }
}

@Composable
fun NftEmptyState() {
    Box(
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(100)
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_screen_warning),
                    tint = white,
                    contentDescription = null,
                    modifier = Modifier
                        .alpha(0.16f)
                        .padding(top = 10.dp)
                        .align(Alignment.TopCenter)
                )
            }
            MarginVertical(margin = 16.dp)
            H3(text = "Your NFT's are going to be here")
//            MarginVertical(margin = 16.dp)
//            B0(
//                text = "Your NFT's are going to be here",
//                color = black2
//            )
        }
    }
}

@Composable
fun NftList(items: List<NftListItem>, appearanceType: NftAppearanceType) {
    when (appearanceType) {
        NftAppearanceType.Grid -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) {
                    GridItem(it)
                }
            }
        }

        NftAppearanceType.List -> {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) {
                    ListItem(it)
                }
            }
        }
    }
}

@Composable
private fun GridItem(item: NftListItem) {
    BackgroundCornered {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, item.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .size(152.dp)
                )
                B1(
                    text = "${item.quantity}/${item.collectionSize}",
                    color = white50,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(black50, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            MarginVertical(margin = 6.dp)
            CapsTitle(text = item.chain, color = white50, modifier = Modifier.width(152.dp))
            H5Bold(text = item.title, modifier = Modifier.width(152.dp))
        }
    }
}

@Composable
private fun ListItem(item: NftListItem) {
    BackgroundCornered(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, item.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .size(64.dp)
                    .align(Alignment.CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Divider(
                color = white16,
                modifier = Modifier
                    .height(64.dp)
                    .width(1.dp)
                    .align(Alignment.CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                CapsTitle(text = item.chain, color = white50)
                MarginVertical(margin = 4.dp)
                H4Bold(text = item.title)
                MarginVertical(margin = 4.dp)
                B2(text = "${item.quantity}/${item.collectionSize}", color = white50)
            }
        }
    }
}

@Composable
fun NftListShimmers(appearanceType: NftAppearanceType) {
    when (appearanceType) {
        NftAppearanceType.Grid -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(3) {
                    GridItemShimmer()
                }
            }
        }

        NftAppearanceType.List -> {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(3) {
                    ListItemShimmer()
                }
            }
        }
    }
}

@Composable
private fun GridItemShimmer() {
    BackgroundCornered(modifier = Modifier.shimmer()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .background(shimmerColor, RoundedCornerShape(size = 16.dp))
                )
            }
            MarginVertical(margin = 6.dp)
            RoundedRect(
                modifier = Modifier
                    .height(13.dp)
                    .width(60.dp)
            )
            MarginVertical(margin = 2.dp)
            RoundedRect(
                modifier = Modifier
                    .height(13.dp)
                    .width(120.dp)
            )

        }
    }
}

@Composable
private fun ListItemShimmer() {
    BackgroundCornered(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(shimmerColor, RoundedCornerShape(size = 16.dp))
                    .align(Alignment.CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Divider(
                color = white16,
                modifier = Modifier
                    .height(64.dp)
                    .width(1.dp)
                    .align(Alignment.CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                RoundedRect(
                    modifier = Modifier
                        .height(13.dp)
                        .width(60.dp)
                )
                MarginVertical(margin = 4.dp)
                RoundedRect(
                    modifier = Modifier
                        .height(13.dp)
                        .width(120.dp)
                )
                MarginVertical(margin = 4.dp)
                RoundedRect(
                    modifier = Modifier
                        .height(13.dp)
                        .width(70.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun NftListPreview() {
    val items = listOf(
        NftListItem(
            "1",
            "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
            "BNB Chain",
            "BORED MARIO v2 #120",
            1,
            290
        ),
        NftListItem(
            "1",
            "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
            "BNB Chain",
            "BORED MARIO v2 #120",
            1,
            290
        ),
        NftListItem(
            "1",
            "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
            "BNB Chain",
            "BORED MARIO v2 #120",
            1,
            290
        )
    )
    val state = NftScreenState.ListState.Content(items)
    val loading = NftScreenState.ListState.Loading
    val empty = NftScreenState.ListState.Empty
   FearlessAppTheme {
       Column {
           Row {
               NftList(state, appearanceType = NftAppearanceType.Grid)
               MarginHorizontal(margin = 8.dp)
               NftList(state, appearanceType = NftAppearanceType.List)
           }
           MarginVertical(margin = 8.dp)
           Row {
               NftList(loading, appearanceType = NftAppearanceType.Grid)
               MarginHorizontal(margin = 8.dp)
               NftList(loading, appearanceType = NftAppearanceType.List)
           }
           MarginVertical(margin = 8.dp)
           NftList(empty, appearanceType = NftAppearanceType.List)
       }
   }
}