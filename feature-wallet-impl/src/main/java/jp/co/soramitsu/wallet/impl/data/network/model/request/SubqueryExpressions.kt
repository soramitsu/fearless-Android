package jp.co.soramitsu.wallet.impl.data.network.model.request

import com.google.gson.Gson

private const val MAX_GRAPHQL_CURSOR_LENGTH = 4096

internal fun graphqlStringLiteral(value: String?): String {
    require(value == null || value.length <= MAX_GRAPHQL_CURSOR_LENGTH) { "GraphQL cursor exceeds the supported length" }
    return Gson().toJson(value)
}

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
