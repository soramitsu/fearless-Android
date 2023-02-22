package jp.co.soramitsu.polkaswap.api.models

/*
If user inputs amount into "from" field - we use the "INPUT" one
If user inputs amount into "to" field - we use the "OUTPUT" one
backString used in the rpc call liquidityProxy_quote SwapVariant parameter
*/
enum class WithDesired(val backString: String) {
    INPUT("WithDesiredInput"),
    OUTPUT("WithDesiredOutput")
}
