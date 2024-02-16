package jp.co.soramitsu.onboarding.impl.welcome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white20
import jp.co.soramitsu.common.compose.theme.white60
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


@Immutable
@JvmInline
value class OnboardingFlow(
    private val flow: List<OnboardingConfig.Variants.ScreenInfo>
): List<OnboardingConfig.Variants.ScreenInfo> by flow

@Stable
interface OnboardingScreenCallback {

    fun onClose()

    fun onNext()

    fun onSkip()

}

@Suppress("FunctionName")
fun NavGraphBuilder.OnboardingScreen(
    onboardingStateFlow: StateFlow<OnboardingFlow?>,
    callback: OnboardingScreenCallback
) {
    composable(WelcomeEvent.Onboarding.PagerScreen.route) {
        val onboardingFlow by onboardingStateFlow.collectAsState()

        OnboardingScreenContent(
            onboardingFlow = onboardingFlow,
            callback = callback
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingScreenContent(
    onboardingFlow: OnboardingFlow?,
    callback: OnboardingScreenCallback
) {
    FullScreenLoading(
        isLoading = onboardingFlow == null,
        contentAlignment = Alignment.BottomStart
    ) {
        onboardingFlow ?: return@FullScreenLoading

        val pagerState = rememberPagerState(
            initialPage = 0,
            initialPageOffsetFraction = 0f,
            pageCount = remember(onboardingFlow) { onboardingFlow::size }
        )

        val currentPageAsState = remember(pagerState) {
            derivedStateOf {
                pagerState.currentPage
            }
        }

        val slideOffsetAsState = remember(pagerState) {
            derivedStateOf {
                pagerState.currentPageOffsetFraction
            }
        }

        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .paint(
                    painter = painterResource(R.drawable.drawable_background_image),
                    contentScale = ContentScale.FillWidth
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Toolbar(
                modifier = Modifier.padding(vertical = 16.dp),
                state = ToolbarViewState(
                    title = "",
                    null,
                    listOf(
                        MenuIconItem(
                            icon = R.drawable.ic_cross_24,
                            onClick = callback::onClose
                        )
                    )
                ),
                onNavigationClick = {}
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    H1(
                        text = onboardingFlow[it].title,
                        color = white,
                        textAlign = TextAlign.Center
                    )

                    B0(
                        text = onboardingFlow[it].description,
                        textAlign = TextAlign.Center,
                        color = white60
                    )

                    MarginVertical(margin = 16.dp)

                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = rememberAsyncImagePainter(model = onboardingFlow[it].image),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomCenter
                    )
                }
            }

            PagerIndicator(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(horizontal = 16.dp),
                gapSize = 7.dp,
                indicatorsSize = onboardingFlow.size,
                currentPage = currentPageAsState,
                slideOffset = slideOffsetAsState
            ) { _ ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(white20, CircleShape),
                )
            }

            AccentButton(
                text = stringResource(id = R.string.common_next),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                onClick = remember(currentPageAsState) {
                    {
                        val page = currentPageAsState.value

                        if (page == onboardingFlow.lastIndex)
                            callback.onNext()
                        else coroutineScope.launch {
                            pagerState.animateScrollToPage(page + 1)
                        }
                    }
                }
            )

            GrayButton(
                text = stringResource(id = R.string.common_skip),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                onClick = callback::onSkip
            )

            MarginVertical(margin = 16.dp)
        }
    }
}

@Composable
@Preview
private fun OnboardingScreenPreview() {
    FearlessAppTheme {
        OnboardingScreenContent(
            OnboardingFlow(
                listOf(
                    OnboardingConfig.Variants.ScreenInfo(
                        title = "Brand new network management",
                        description = "Navigate between All, Popular and your Favourite networks modes",
                        image = "${R.drawable.drawable_background_image}"
                    ),
                    OnboardingConfig.Variants.ScreenInfo(
                        title = "Title1",
                        description = "Description1",
                        image = "image1"
                    ),
                    OnboardingConfig.Variants.ScreenInfo(
                        title = "Title2",
                        description = "Description2",
                        image = "image2"
                    )
                )
            ),
            object : OnboardingScreenCallback {
                override fun onClose() = Unit

                override fun onNext() = Unit

                override fun onSkip() = Unit
            }
        )
    }
}