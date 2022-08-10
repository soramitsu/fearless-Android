package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.presentation.LoadingState

@Composable
fun AboutScreen(
    viewModel: AboutViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.value

    when (state) {
        is LoadingState.Loading -> {}
        is LoadingState.Loaded -> { state.data }
    }

    Card(
        backgroundColor = Color.Black,
        elevation = 0.dp,
        shape = RoundedCornerShape(5.dp),
        content = {
            Text("Hello Compose!!!", color = Color.White)
        }
    )
}

data class Message(val author: String, val body: String)

@Composable
fun MessageCard(msg: Message) {
    Row(Modifier.background(color = MaterialTheme.customColors.black)) {
        Image(
            painter = painterResource(R.drawable.ic_copy_24),
            contentDescription = "Contact profile picture"
        )

        Column {
            Text(
                text = msg.author,
                color = MaterialTheme.customColors.white
            )
            Text(
                text = msg.body,
                style = MaterialTheme.customTypography.body0,
                color = MaterialTheme.customColors.white
            )
        }
    }
}

@Composable
fun MessageCard2(msg: Message) {
    // Add padding around our message
    Row(modifier = Modifier.padding(all = 2.dp)) {
        Image(
            painter = painterResource(R.drawable.about_top_background),
            contentDescription = "Contact profile picture",
            modifier = Modifier
                // Set image size to 40 dp
                .size(40.dp)
                // Clip image to be shaped as a circle
                .clip(CircleShape)
        )

        // Add a horizontal space between the image and the column
        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(text = msg.author)
            // Add a vertical space between the author and message texts
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = msg.body,
                style = MaterialTheme.customTypography.body0,
                color = MaterialTheme.customColors.white
            )
        }
    }
}

@Preview
@Composable
fun PreviewMessageCard() {
    FearlessTheme {
        Column {
            MessageCard(
                msg = Message("Colleague", "Hey, take a look at Jetpack Compose, it's great!")
            )

            MessageCard2(
                msg = Message("Colleague", "Hey, take a look at Jetpack Compose, it's great!")
            )
        }
    }
}
