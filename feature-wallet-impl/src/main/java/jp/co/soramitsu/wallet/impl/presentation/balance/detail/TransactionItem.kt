package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance

@Composable
fun TransactionItem1(
    item: OperationModel,
    transactionClicked: (OperationModel) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            transactionClicked(item)
        }
    ) {
        AsyncImage(
            model = when (item.assetIconUrl) {
                null -> item.operationIcon
                else -> getImageRequest(LocalContext.current, item.assetIconUrl)
            },
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
            B1(
                text = item.header,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            B2(
                text = item.subHeader,
                textAlign = TextAlign.Start,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f)
            )
        }
        MarginHorizontal(margin = 4.dp)

        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                B1(
                    modifier = Modifier.fillMaxWidth(),
                    text = item.amount,
                    color = item.amountColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )

                if (item.statusAppearance != OperationStatusAppearance.COMPLETED) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Image(
                        res = item.statusAppearance.icon,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
            }
            B2(
                text = item.time.formatDateTime(LocalContext.current).toString(),
                textAlign = TextAlign.End,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f)
            )
        }
    }
}

@Composable
fun TransactionItem(
    item: OperationModel,
    transactionClicked: (OperationModel) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                transactionClicked(item)
            }
    ) {
        ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
            val (image, imageSpacer, header, amount, status, subHeader, time) = createRefs()

            AsyncImage(
                model = when (item.assetIconUrl) {
                    null -> item.operationIcon
                    else -> getImageRequest(LocalContext.current, item.assetIconUrl)
                },
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .constrainAs(image) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    }
            )
            Spacer(modifier = Modifier
                .width(8.dp)
                .constrainAs(imageSpacer) {
                    start.linkTo(image.end)
                })
            B1(
                text = item.header,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.constrainAs(header) {
                    top.linkTo(parent.top)
                    start.linkTo(imageSpacer.end, margin = 8.dp)
                    end.linkTo(amount.start, margin = 4.dp)
                    width = Dimension.percent(0.2f)
                }
            )
            B1(
                text = item.amount,
                color = item.amountColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .constrainAs(amount) {
                        end.linkTo(status.start)
                        top.linkTo(parent.top)
                        start.linkTo(header.end)
                        width = Dimension.fillToConstraints
                    }
            )
            if (item.statusAppearance != OperationStatusAppearance.COMPLETED) {
                Image(
                    res = item.statusAppearance.icon,
                    modifier = Modifier
                        .size(14.dp)
                        .constrainAs(status) {
                            end.linkTo(parent.end)
                            top.linkTo(parent.top)
                        }
                )
            }
            createHorizontalChain(image, imageSpacer, header, amount, status, chainStyle = ChainStyle.SpreadInside)
            B2(
                text = item.subHeader,
                textAlign = TextAlign.Start,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f),
                modifier = Modifier.constrainAs(subHeader) {
                    top.linkTo(header.bottom)
                    start.linkTo(imageSpacer.end)
                    bottom.linkTo(parent.bottom)
                    end.linkTo(time.start)
                    width = Dimension.fillToConstraints
                }
            )

            B2(
                text = item.time.formatDateTime(LocalContext.current).toString(),
                textAlign = TextAlign.End,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f),
                modifier = Modifier.constrainAs(time) {
                    top.linkTo(amount.bottom)
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                    width = Dimension.fillToConstraints
                }
            )
        }
    }
}


@Composable
@Preview
private fun PreviewTransactionItem() {
    Column {
        TransactionItem(
            item = OperationModel(
                id = "",
                time = System.currentTimeMillis(),
                header = "HeaderHeaderHeaderHeaderHeaderHeaderHeaderHeader",
                statusAppearance = OperationStatusAppearance.COMPLETED,
                amount = "amountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamountamount",
                operationIcon = null,
                subHeader = "subHeadersubsubHeadersubsubHeadersubsubHeadersub"
            ),
            transactionClicked = {}
        )
        TransactionItem1(
            item = OperationModel(
                id = "",
                time = System.currentTimeMillis(),
                header = "HeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeaderHeader",
                statusAppearance = OperationStatusAppearance.COMPLETED,
                amount = "123123123123123123123123123123123123",
                operationIcon = null,
                subHeader = "subHeadersubHeadersubHeadersubHeadersubHeadersubHeadersubHeadersubHeadersubHeadersubHeadersubHeadersubHeader"
            ),
            transactionClicked = {}
        )
    }
}
