package jp.co.soramitsu.common.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData

fun <FROM, TO> LiveData<FROM>.map(mapper: (FROM) -> TO) : LiveData<TO> {
    return MediatorLiveData<TO>().apply {
        addSource(this@map) {
            value = mapper.invoke(it)
        }
    }
}

fun <FROM, TO> LiveData<FROM>.mapMutable(mapper: (FROM) -> TO) : MutableLiveData<TO> {
    return MediatorLiveData<TO>().apply {
        addSource(this@mapMutable) {
            value = mapper.invoke(it)
        }
    }
}

fun <FIRST, SECOND, RESULT> LiveData<FIRST>.combine(another: LiveData<SECOND>, zipper: (FIRST, SECOND) -> RESULT)
    : LiveData<RESULT> {

    return MediatorLiveData<RESULT>().apply {
        addSource(this@combine) { first ->
            val second = another.value

            if (first != null && second != null) {
                value = zipper.invoke(first, second)
            }
        }

        addSource(another) { second ->
            val first = this@combine.value

            if (first != null && second != null) {
                value = zipper.invoke(first, second)
            }
        }
    }
}