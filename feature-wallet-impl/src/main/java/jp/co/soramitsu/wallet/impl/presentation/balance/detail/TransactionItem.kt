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
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance

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
            val (image, imageSpacer, header, amount, statusSpacer, status, subHeader, time) = createRefs()

            val amountModifier = if (item.type == OperationModel.Type.Transfer) {
                Modifier.constrainAs(amount) {
                    end.linkTo(statusSpacer.start)
                    top.linkTo(parent.top)
                }
            } else {
                Modifier.constrainAs(amount) {
                    end.linkTo(statusSpacer.start)
                    top.linkTo(parent.top)
                    start.linkTo(header.end)
                    width = Dimension.fillToConstraints
                }
            }

            val headerModifier = if (item.type == OperationModel.Type.Transfer) {
                Modifier.constrainAs(header) {
                    top.linkTo(parent.top)
                    start.linkTo(imageSpacer.end)
                    end.linkTo(amount.start)
                    width = Dimension.fillToConstraints
                }
            } else {
                Modifier.constrainAs(header) {
                    top.linkTo(parent.top)
                    start.linkTo(imageSpacer.end)
                }
            }

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
            Spacer(
                modifier = Modifier
                    .width(8.dp)
                    .constrainAs(imageSpacer) {
                        start.linkTo(image.end)
                    }
            )
            B1(
                text = item.header,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = headerModifier
            )
            B1(
                text = item.amount,
                color = item.amountColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = amountModifier
            )
            if (item.statusAppearance != OperationStatusAppearance.COMPLETED) {
                Spacer(
                    modifier = Modifier
                        .width(8.dp)
                        .constrainAs(statusSpacer) {
                            end.linkTo(status.start)
                        }
                )

                Image(
                    res = item.statusAppearance.icon,
                    modifier = Modifier
                        .size(14.dp)
                        .constrainAs(status) {
                            end.linkTo(parent.end)
                            top.linkTo(amount.top)
                            bottom.linkTo(amount.bottom)
                        }
                )
            }
            createHorizontalChain(image, imageSpacer, header, amount, statusSpacer, status, chainStyle = ChainStyle.SpreadInside)
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
                header = "Swap",
                statusAppearance = OperationStatusAppearance.PENDING,
                amount = "1,232.9323234234 XOR -> 90,542.1234513123 VAL",
                operationIcon = null,
                subHeader = "subHeadersubsubHeadersubsubHeadersubsubHeadersub",
                type = OperationModel.Type.Swap
            ),
            transactionClicked = {}
        )
        TransactionItem(
            item = OperationModel(
                id = "",
                time = System.currentTimeMillis(),
                header = "cnUz6GgQd8oZDQ3wbnrJUrxGxJGYnLGDWVRzBW1U7K1mJ8nMD",
                statusAppearance = OperationStatusAppearance.FAILED,
                amount = "+0.00000000123 XOR",
                operationIcon = null,
                subHeader = "subHeadersubsubHeadersubsubHeadersubsubHeadersub",
                type = OperationModel.Type.Transfer
            ),
            transactionClicked = {}
        )
    }
}
