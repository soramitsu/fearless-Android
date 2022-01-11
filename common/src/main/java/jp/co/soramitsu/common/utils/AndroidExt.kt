package jp.co.soramitsu.common.utils

import android.database.Cursor

inline fun <T> Cursor.map(iteration: Cursor.() -> T): List<T> {
    val result = mutableListOf<T>()

    while (moveToNext()) {
        result.add(iteration())
    }

    return result
}
