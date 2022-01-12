package jp.co.soramitsu.common.utils

class IgnoredOnEquals<out T>(val value: T) {

    override fun equals(other: Any?): Boolean {
        return true
    }
}
