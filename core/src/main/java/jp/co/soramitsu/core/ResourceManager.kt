package jp.co.soramitsu.core

interface ResourceManager {

    fun getString(res: Int): String

    fun getColor(res: Int): Int

    fun getQuantityString(id: Int, quantity: Int): String
}