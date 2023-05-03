package jp.co.soramitsu.common.compose.component

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.MAX_DECIMALS_8
import jp.co.soramitsu.ui_core.component.input.number.BasicNumberInput
import jp.co.soramitsu.ui_core.theme.customTypography
import java.math.BigDecimal

data class AmountInputViewState(
    val tokenName: String? = null,
    val tokenImage: String? = null,
    val totalBalance: String,
    val fiatAmount: String?,
    val tokenAmount: BigDecimal,
    val title: String? = null,
    val isActive: Boolean = true,
    val isFocused: Boolean = false,
    val allowAssetChoose: Boolean = false,
    val precision: Int = MAX_DECIMALS_8,
    val initial: BigDecimal?
) {
    companion object {
        fun default(resourceManager: ResourceManager, @StringRes totalBalanceFormat: Int = R.string.common_balance_format): AmountInputViewState {
            return AmountInputViewState(
                tokenName = null,
                tokenImage = null,
                totalBalance = resourceManager.getString(totalBalanceFormat, "0"),
                fiatAmount = "$0",
                tokenAmount = BigDecimal.ZERO,
                initial = null
            )
        }
    }
}

@Composable
fun AmountInput(
    state: AmountInputViewState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    borderColor: Color = white24,
    borderColorFocused: Color = Color.Unspecified,
    focusRequester: FocusRequester? = null,
    onInput: (BigDecimal?) -> Unit = {},
    onInputFocusChange: (Boolean) -> Unit = {},
    onTokenClick: () -> Unit = {}
) {
    val textColorState = when {
        state.tokenAmount.compareTo(BigDecimal.ZERO) == 0 -> {
            black2
        }
        state.isActive -> {
            white
        }
        else -> {
            black2
        }
    }

    val assetColorState = when {
        state.isActive -> {
            white
        }
        else -> {
            black2
        }
    }

    val borderColorState = when {
        !state.isFocused -> borderColor
        borderColorFocused.isUnspecified -> borderColor
        else -> borderColorFocused
    }

    BackgroundCorneredWithBorder(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = backgroundColor,
        borderColor = borderColorState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                val title = state.title ?: stringResource(id = R.string.common_amount)
                H5(text = title, modifier = Modifier.weight(1f), color = black2)
                state.fiatAmount?.let {
                    B1(text = it, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = black2)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = if (state.allowAssetChoose) {
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onTokenClick)
                    } else {
                        Modifier
                    }
                ) {
                    TokenIcon(url = state.tokenImage)
                    MarginHorizontal(margin = 4.dp)
                    val tokenName = state.tokenName?.uppercase() ?: stringResource(R.string.common_select_asset)
                    H3(text = tokenName, modifier = Modifier.align(CenterVertically), color = assetColorState)
                    MarginHorizontal(margin = 8.dp)
                    if (state.allowAssetChoose) {
                        Image(
                            res = R.drawable.ic_arrow_down,
                            modifier = Modifier
                                .align(CenterVertically)
                                .padding(top = 4.dp, end = 4.dp)
                        )
                    }
                }
                BasicNumberInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("InputAmountField" + (state.tokenName.orEmpty()))
                        .wrapContentHeight(),
                    onFocusChanged = onInputFocusChange,
                    textStyle = MaterialTheme.customTypography.displayS.copy(textAlign = TextAlign.End, color = textColorState),
                    enabled = true,
                    precision = state.precision,
                    initial = state.initial,
                    onValueChanged = onInput,
                    focusRequester = focusRequester,
                    cursorColor = colorAccentDark,
                    placeholder = {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            text = "0",
                            style = MaterialTheme.customTypography.displayS,
                            textAlign = TextAlign.End,
                            color = black2
                        )
                    }
                )
            }
            MarginVertical(margin = 4.dp)
            B1(text = state.totalBalance, color = black2)
        }
    }
}

@Composable
private fun RowScope.TokenIcon(
    url: String?,
    modifier: Modifier = Modifier
) {
    val imageModifier = modifier
        .size(28.dp)
        .padding(2.dp)
        .align(CenterVertically)
    if (url != null) {
        AsyncImage(
            model = getImageRequest(LocalContext.current, url),
            contentDescription = null,
            modifier = imageModifier
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_token_undefined),
            contentDescription = null,
            modifier = imageModifier,
            tint = Color.Unspecified
        )
    }
}

@Composable
@Preview
private fun AmountInputPreview() {
    val state = AmountInputViewState(
        tokenName = "KSM",
        tokenImage = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        totalBalance = "Balance: 20.0",
        fiatAmount = "$120.0",
        tokenAmount = BigDecimal.ONE,
        allowAssetChoose = true,
        initial = null
    )
    FearlessTheme {
        AmountInput(state)
    }
}
