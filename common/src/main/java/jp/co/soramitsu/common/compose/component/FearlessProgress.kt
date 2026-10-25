package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
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
        val still = LocalFwEffects.current.reduceMotion
        Row(
            modifier = modifier.widthIn(max = 280.dp).fillMaxWidth()
                .background(c.surfaceRaised, RoundedCornerShape(16.dp))
                .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (still) {
                Canvas(Modifier.size(48.dp).padding(4.dp)) {
                    drawArc(c.strokeStrong, 0f, 360f, false, style = Stroke(4.dp.toPx()))
                    drawArc(c.textLink, -90f, 100f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp).padding(4.dp).clearAndSetSemantics {},
                    color = c.textLink,
                    strokeWidth = 4.dp
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.fw_loading_in_progress), modifier = Modifier.weight(1f),
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
