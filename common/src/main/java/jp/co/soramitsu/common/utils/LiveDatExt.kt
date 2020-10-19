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

@Suppress("UNCHECKED_CAST")
class ComponentHolder(val values: List<*>) {
    operator fun <T> component1() = values.first() as T
    operator fun <T> component2() = values[1] as T
    operator fun <T> component3() = values[2] as T
    operator fun <T> component4() = values[3] as T
    operator fun <T> component5() = values[4] as T
}

/**
 * Supports up to N sources, where N is last componentN() in ComponentHolder
 * @see ComponentHolder
 */
fun <R> combine(
    vararg sources: LiveData<*>,
    combiner: (ComponentHolder) -> R
): LiveData<R> {
    return MediatorLiveData<R>().apply {
        for (source in sources) {
            addSource(source) {
                val values = sources.map { it.value }

                val nonNull = values.filterNotNull()

                if (nonNull.size == values.size) {
                    value = combiner.invoke(ComponentHolder(nonNull))
                }
            }
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

fun <T> LiveData<T>.distinctUntilChanged() = Transformations.distinctUntilChanged(this)

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