package jp.co.soramitsu.wallet.impl.domain.model

/** Legacy construction is explicit; unavailable values are never routed through nullable copies. */
fun <T : Any> T?.asAssetAmountState(): AssetAmountState<T> =
    if (this == null) AssetAmountState.Missing else AssetAmountState.Known(this)

fun <T> AssetAmountState<T>.knownOrNull(): T? = (this as? AssetAmountState.Known<T>)?.value

fun <T, R> AssetAmountState<T>.mapAssetAmount(transform: (T) -> R): AssetAmountState<R> = when (this) {
    AssetAmountState.Missing -> AssetAmountState.Missing
    is AssetAmountState.Unavailable -> this
    is AssetAmountState.Known -> AssetAmountState.Known(transform(value))
}

fun <T, U, R> combineAssetAmountStates(
    first: AssetAmountState<T>,
    second: AssetAmountState<U>,
    operation: (T, U) -> R
): AssetAmountState<R> = when {
    first is AssetAmountState.Unavailable -> first
    second is AssetAmountState.Unavailable -> second
    first is AssetAmountState.Missing || second is AssetAmountState.Missing -> AssetAmountState.Missing
    first is AssetAmountState.Known && second is AssetAmountState.Known -> AssetAmountState.Known(operation(first.value, second.value))
    else -> error("Unrecognized amount state")
}
