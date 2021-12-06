package jp.co.soramitsu.common.utils

import java.util.regex.Pattern

class EmailValidator {
    companion object {
        @JvmStatic
        private val pattern = Pattern.compile(
            "^[a-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\$",
            Pattern.CASE_INSENSITIVE
        )

        @JvmStatic
        fun isValid(emailStr: String): Boolean {
            val matcher = pattern.matcher(emailStr)
            return matcher.find()
        }
    }
}
