package com.huanchengfly.tieba.post.utils

import android.view.KeyEvent
import android.view.View
import com.huanchengfly.tieba.post.R

private class KeyEventManager<V : View> : View.OnKeyListener {
    private val keyEventMapping = mutableMapOf<Int, ((V) -> Unit)>()

    override fun onKey(v: View, keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_UP && keyEventMapping.containsKey(keyCode)) {
            keyEventMapping[keyCode]?.invoke(v as V)
            return true
        }
        return false
    }

    fun bindKeyEvent(keyCode: Int, action: (V) -> Unit) {
        keyEventMapping[keyCode] = action
    }
}

fun <V : View> V.bindKeyEvent(keyCode: Int, action: (V) -> Unit) {
    val keyEventManager = getKeyEventManager()
    keyEventManager.bindKeyEvent(keyCode, action)
}

fun <V : View> V.bindKeyEvent(keyCodes: List<Int>, action: (V) -> Unit) {
    val keyEventManager = getKeyEventManager()
    keyCodes.forEach {
        keyEventManager.bindKeyEvent(it, action)
    }
}

private fun <V : View> V.getKeyEventManager(): KeyEventManager<V> {
    val existingManager = getTag(R.id.view_key_event_manager_tag) as? KeyEventManager<*>
    if (existingManager != null) {
        @Suppress("UNCHECKED_CAST")
        return existingManager as KeyEventManager<V>
    }
    return KeyEventManager<V>().also {
        setTag(R.id.view_key_event_manager_tag, it)
        setOnKeyListener(it)
    }
}
