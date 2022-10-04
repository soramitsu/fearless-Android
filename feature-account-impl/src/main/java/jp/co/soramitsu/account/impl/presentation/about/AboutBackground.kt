package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.feature_account_impl.R

@Composable
fun AboutBackground() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Image(
                modifier = Modifier.fillMaxWidth(),
                painter = painterResource(id = R.drawable.about_top_background),
                contentScale = ContentScale.FillWidth,
                contentDescription = null
            )
            Image(
                modifier = Modifier.fillMaxWidth(),
                painter = painterResource(id = R.drawable.about_top_bg_gradient),
                contentScale = ContentScale.FillWidth,
                contentDescription = null
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(color = Color.Black)
        )
    }
}

@Composable
@Preview
fun PreviewAboutBackground() {
    AboutBackground()
}
