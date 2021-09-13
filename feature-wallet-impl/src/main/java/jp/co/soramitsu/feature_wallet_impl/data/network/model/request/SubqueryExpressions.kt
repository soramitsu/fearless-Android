package jp.co.soramitsu.feature_wallet_impl.data.network.model.request

object SubqueryExpressions {

    fun or(vararg innerExpressions: String): String {
        return compoundExpression("or", *innerExpressions)
    }

    fun or(innerExpressions: Collection<String>) = or(*innerExpressions.toTypedArray())

    fun anyOf(innerExpressions: Collection<String>) = or(innerExpressions)

    fun and(vararg innerExpressions: String): String {
        return compoundExpression("and", *innerExpressions)
    }

    fun and(innerExpressions: Collection<String>) = and(*innerExpressions.toTypedArray())

    fun not(expression: String): String {
        return "not: {$expression}"
    }

    private fun compoundExpression(name: String, vararg innerExpressions: String): String {
        require(innerExpressions.isNotEmpty())

        if (innerExpressions.size == 1) {
            return innerExpressions.first()
        }

        return innerExpressions.joinToString(
            prefix = "$name: [",
            postfix = "]",
            separator = ","
        ) {
            "{$it}"
        }
    }
}
