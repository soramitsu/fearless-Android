package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import jp.co.soramitsu.common.compose.theme.fw.FwMirrorLoader
import androidx.compose.material.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white08

@Composable
fun FearlessProgress(modifier: Modifier = Modifier) {
    if (FwRestyle.isEnabled(FwSurface.Loading)) {
        val c = LocalFwColors.current
        // The mirror loader (brand kit SPEC v4 part 4): three comets on the mirror's rim, 96 dp, over the
        // app's own words. Under reduced motion the comets stand still and light in turn.
        Column(
            modifier = modifier.widthIn(max = 280.dp)
                .background(c.surfaceRaised, RoundedCornerShape(16.dp))
                .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FwMirrorLoader(Modifier.size(96.dp))
            Text(stringResource(R.string.fw_loading_in_progress),
                color = c.textPrimary, style = fwTextStyle(FwTextRole.BodyM))
        }
        return
    }
    Box(
        modifier = modifier
            .size(88.dp)
            .background(grayButtonBackground.copy(alpha = 0.7f), RoundedCornerShape(size = 16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .border(4.dp, white08, shape = CircleShape)
                .background(transparent, shape = CircleShape)
        )
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .align(Alignment.Center),
            color = colorAccentDark,
            strokeWidth = 4.dp
        )
        Image(
            res = R.drawable.ic_fearless_logo,
            tint = colorAccentDark,
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.Center)
        )
    }
}

@Composable
@Preview
private fun FearlessProgressPreview() {
    FearlessTheme {
        FearlessProgress()
    }
}
