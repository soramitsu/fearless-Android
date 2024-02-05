package jp.co.soramitsu.nft.impl.presentation.details

import android.graphics.drawable.PictureDrawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GifImage
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.ShimmerB1
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.utils.clickableSingle

data class NftDetailsScreenState(
    val name: String = "",
    val imageUrl: String = "",
    val hasImageRequestFailed: Boolean = false,
    val isImageShimmerEnabled: Boolean = false,
    val description: String = "",
    val collectionName: String = "",
    val owner: String = "",
    val ownerIcon: PictureDrawable? = null,
    val tokenId: String = "",
    val creator: String = "",
    val creatorIcon: PictureDrawable? = null,
    val network: String = "",
    val tokenType: String = "",
    val dateTime: String = "",
    val price: String = "",
    val priceFiat: String = ""
)

interface NftDetailsScreenInterface {
    fun shareClicked()

    fun creatorClicked()

    fun tokenIdClicked()

    fun ownerClicked()
}

@Composable
fun NftDetailsScreen(viewModel: NftDetailsViewModel, onCloseClick: () -> Unit) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    NftDetailsScreen(state, viewModel, onCloseClick)
}

@Composable
fun NftDetailsScreen(
    state: NftDetailsScreenState,
    screenInterface: NftDetailsScreenInterface,
    onCloseClick: () -> Unit
) {
    val shimmerEnabled = state.creator.isEmpty()

    BottomSheetScreen {
        Toolbar(
            state = ToolbarViewState(
                state.name,
                null,
                MenuIconItem(icon = R.drawable.ic_cross_24, onCloseClick)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ShimmeredImage(state.imageUrl, state.creator.isEmpty())

            MarginVertical(margin = 16.dp)

            ActionButtons(
                onSendClicked = {},
                onShareClicked = screenInterface::shareClicked,
                isEnabled = !shimmerEnabled
            )

            MarginVertical(margin = 8.dp)

            if (shimmerEnabled) {
                ShimmeredNftDescription()
                MarginVertical(margin = 8.dp)
            } else {
                B1(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    text = state.description,
                    color = MaterialTheme.customColors.gray2
                )
            }

            if (shimmerEnabled || state.collectionName.isNotEmpty()) {
                DetailRowItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(id = R.string.nft_collection_title),
                    value = state.collectionName,
                    shimmerFraction = 0.5f,
                    shimmerEnabled = shimmerEnabled
                )

                Divider(
                    color = black3,
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            if (shimmerEnabled || state.owner.isNotEmpty()) {
                DetailRowItem(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickableSingle { screenInterface.ownerClicked() },
                    title = stringResource(id = R.string.nft_owner_title),
                    value = state.owner,
                    leftIcon = state.ownerIcon,
                    rightIcon = R.drawable.ic_share_arrow_white_24,
                    shimmerFraction = 0.5f,
                    shimmerEnabled = shimmerEnabled
                )

                Divider(
                    color = black3,
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            if (shimmerEnabled || state.tokenId.isNotEmpty()) {
                DetailRowItem(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickableSingle { screenInterface.tokenIdClicked() },
                    title = stringResource(id = R.string.nft_tokenid_title),
                    rightIcon = R.drawable.ic_share_arrow_white_24,
                    value = state.tokenId,
                    shimmerFraction = 0.1f,
                    shimmerEnabled = shimmerEnabled
                )

                Divider(
                    color = black3,
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            DetailRowItem(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickableSingle { screenInterface.creatorClicked() },
                title = stringResource(id = R.string.nft_creator_title),
                value = state.creator,
                leftIcon = state.creatorIcon,
                rightIcon = R.drawable.ic_share_arrow_white_24,
                shimmerFraction = 0.5f,
                shimmerEnabled = shimmerEnabled
            )

            Divider(
                color = black3,
                modifier = Modifier
                    .height(1.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )

            DetailRowItem(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(id = R.string.common_network),
                value = state.network,
                shimmerFraction = 0.2f,
                shimmerEnabled = shimmerEnabled
            )

            Divider(
                color = black3,
                modifier = Modifier
                    .height(1.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )

            if (shimmerEnabled || state.tokenType.isNotEmpty()) {
                DetailRowItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(id = R.string.nft_token_type_title),
                    value = state.tokenType,
                    shimmerFraction = 0.2f,
                    shimmerEnabled = shimmerEnabled
                )

                Divider(
                    color = black3,
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            if (shimmerEnabled || state.dateTime.isNotEmpty()) {
                DetailRowItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(id = R.string.common_date),
                    value = state.dateTime,
                    shimmerFraction = 0.3f,
                    shimmerEnabled = shimmerEnabled
                )

                Divider(
                    color = black3,
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            if (shimmerEnabled || state.price.isNotEmpty()) {
                DetailRowItem(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(id = R.string.common_price),
                    value = state.price,
                    shimmerFraction = 0.3f,
                    shimmerEnabled = shimmerEnabled
                )

                Divider(
                    color = black3,
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            MarginVertical(margin = 16.dp)
        }
    }
}

@Suppress("MagicNumber")
@Composable
fun ShimmeredNftDescription() {
    MarginVertical(margin = 8.dp)
    ShimmerB1(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .padding(horizontal = 16.dp)
    )
    MarginVertical(margin = 4.dp)
    ShimmerB1(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .padding(horizontal = 16.dp)
    )
    MarginVertical(margin = 4.dp)
    ShimmerB1(
        modifier = Modifier
            .fillMaxWidth(0.3f)
            .padding(horizontal = 16.dp)
    )
}

@Composable
fun ActionButtons(
    onSendClicked: () -> Unit,
    onShareClicked: () -> Unit,
    isEnabled: Boolean
) {
    AccentButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp),
        enabled = isEnabled,
        text = stringResource(id = R.string.common_action_send),
        iconRes = R.drawable.ic_send_outlined,
        onClick = onSendClicked
    )

    MarginVertical(margin = 8.dp)

    AccentButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp),
        enabled = isEnabled,
        text = stringResource(id = R.string.common_share),
        iconRes = R.drawable.ic_share_arrow_white_24,
        onClick = onShareClicked
    )
}

@Suppress("MagicNumber")
@Composable
private fun DetailRowItem(
    modifier: Modifier,
    title: String,
    value: String,
    leftIcon: PictureDrawable? = null,
    @DrawableRes rightIcon: Int? = null,
    shimmerFraction: Float = 0.3f,
    shimmerEnabled: Boolean = false
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        B1(modifier = Modifier.wrapContentWidth(), text = title, color = MaterialTheme.customColors.gray1)
        Spacer(
            modifier = modifier
                .fillMaxWidth()
                .weight(1f)
        )

        MarginHorizontal(margin = 8.dp)
        if (shimmerEnabled) {
            ShimmerB1(modifier = Modifier.fillMaxWidth(shimmerFraction))
        } else {
            leftIcon?.let {
                androidx.compose.foundation.Image(bitmap = it.toBitmap().asImageBitmap(), contentDescription = "")
                MarginHorizontal(margin = 8.dp)
            }
            B1(maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.9f), textAlign = TextAlign.End, text = value)
            rightIcon?.let {
                MarginHorizontal(margin = 4.dp)
                Image(res = it, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ShimmeredImage(imageUrl: String, shimmerEnabled: Boolean) {
    if (imageUrl.isEmpty() && shimmerEnabled) {
        Shimmer(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
        )
    } else {
        NftAsyncImage(imageUrl)
    }
}

@Composable
private fun NftAsyncImage(imageUrl: String) {
    SubcomposeAsyncImage(
        model = getImageRequest(LocalContext.current, imageUrl),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        error = {
            GifImage(gifResource = R.drawable.animated_bird)
        },
        loading = {
            Shimmer(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .fillMaxWidth()
            .aspectRatio(1f)
    )
}

@Preview
@Composable
fun NftCollectionScreenPreview() {
    val previewState = NftDetailsScreenState(
        name = "Custom Pixel Babe",
        imageUrl = "",
        description = "Custom Pixel Babe it's very limited bunch of NFT's. Only 10 be made. It's miscellaneous mix from 3D animation and electronic acid music loops.",
        collectionName = "\"Custom  BabeBabeBabe\" collection",
        owner = "E3WVE....Jrpp",
        ownerIcon = null,
        tokenId = "123",
        creator = "Birds collection",
        creatorIcon = null,
        network = "Polygon",
        tokenType = "ERC1155",
        dateTime = "December 17, 2022 03:24",
        price = "0.5 MATIC",
        priceFiat = "~$26.95"
    )

    FearlessAppTheme {
        NftDetailsScreen(
            previewState,
            object : NftDetailsScreenInterface {
                override fun shareClicked() = Unit

                override fun creatorClicked() = Unit

                override fun tokenIdClicked() = Unit

                override fun ownerClicked() = Unit
            },
            {}
        )
    }
}
