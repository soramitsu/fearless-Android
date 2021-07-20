package jp.co.soramitsu.app

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader


fun Any.getResourceReader(fileName: String): Reader {
    val stream = javaClass.classLoader!!.getResourceAsStream(fileName)

    return BufferedReader(InputStreamReader(stream))
}
