package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.compose.theme.black
import jp.co.soramitsu.common.compose.theme.shimmerColor

@Composable
fun ShimmerB0(modifier: Modifier = Modifier) {
    Shimmer(
        modifier = modifier
            .height(17.dp)
    )
}

@Composable
fun ShimmerB2(modifier: Modifier = Modifier) {
    Shimmer(
        modifier = modifier
            .height(13.dp)
    )
}

@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shimmer(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerColor, RoundedCornerShape(size = 50.dp))
        )
    }
}

@Composable
fun ShimmerRectangle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shimmer(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerColor, RoundedCornerShape(size = 16.dp))
        )
    }
}

@Preview
@Composable
private fun ShimmerPreview() {
    Box(modifier = Modifier.background(color = black)) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerB0(Modifier.width(160.dp))
            MarginVertical(margin = 16.dp)
            ShimmerB2(Modifier.width(120.dp))
        }
    }
}
