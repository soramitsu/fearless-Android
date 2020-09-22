package jp.co.soramitsu.common.utils

import android.widget.EditText
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations

fun <FROM, TO> LiveData<FROM>.map(mapper: (FROM) -> TO): LiveData<TO> {
    return Transformations.map(this, mapper)
}

fun <FROM, TO> LiveData<FROM>.mapMutable(mapper: (FROM) -> TO): MutableLiveData<TO> {
    return MediatorLiveData<TO>().apply {
        addSource(this@mapMutable) {
            value = mapper.invoke(it)
        }
    }
}

fun <FIRST, SECOND, RESULT> LiveData<FIRST>.combine(
    another: LiveData<SECOND>,
    zipper: (FIRST, SECOND) -> RESULT
): LiveData<RESULT> {

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

fun <FROM, TO> LiveData<FROM>.switchMap(
    mapper: (FROM) -> LiveData<TO>,
    triggerOnSwitch: Boolean = true
): LiveData<TO> {
    val result: MediatorLiveData<TO> = MediatorLiveData()

    result.addSource(this, object : Observer<FROM> {
        var mSource: LiveData<TO>? = null

        override fun onChanged(x: FROM) {
            val newLiveData: LiveData<TO> = mapper.invoke(x)

            if (mSource === newLiveData) {
                return
            }
            if (mSource != null) {
                result.removeSource(mSource!!)
            }

            mSource = newLiveData

            if (mSource != null) {
                result.addSource(mSource!!) { y -> result.setValue(y) }

                if (triggerOnSwitch) {
                    mSource!!.notifyObservers()
                }
            }
        }
    })

    return result
}

fun EditText.bindTo(liveData: MutableLiveData<String>, lifecycleOwner: LifecycleOwner) {
    onTextChanged {
        if (liveData.value != it) {
            liveData.value = it
        }
    }

    liveData.observe(lifecycleOwner, Observer {
        if (it != text.toString()) {
            setText(it)
        }
    })
}

fun LiveData<String>.isNotEmpty() = !value.isNullOrEmpty()

fun <T> LiveData<T>.notifyObservers() {
    (this as MutableLiveData<T>).value = value
}