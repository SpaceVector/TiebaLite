package com.huanchengfly.tieba.post

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object DataStoreConst {
    const val DATA_STORE_NAME = "app_preferences"
}

private val dataStoreWriteScope by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
private val dataStoreReadScope by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
@Volatile
private var latestDataStorePreferences: Preferences? = null
@Volatile
private var dataStoreSnapshotStarted = false

private val dataStoreInstance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
    preferencesDataStore(
        name = DataStoreConst.DATA_STORE_NAME,
        produceMigrations = { context ->
            listOf(
                SharedPreferencesMigration(context, "settings"),
                object : DataMigration<Preferences> {
                    override suspend fun cleanUp() {}

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        return currentData.toMutablePreferences().apply {
                            set(stringPreferencesKey("dark_theme"), "grey_dark")
                        }.toPreferences()
                    }

                    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                        return currentData[stringPreferencesKey("dark_theme")] == "dark"
                    }
                }
            )
        }
    )
}

val Context.dataStore: DataStore<Preferences> by dataStoreInstance

private fun ensureDataStoreSnapshot() {
    if (dataStoreSnapshotStarted || !App.isInitialized) {
        return
    }
    synchronized(DataStoreConst::class.java) {
        if (dataStoreSnapshotStarted || !App.isInitialized) {
            return
        }
        dataStoreSnapshotStarted = true
        dataStoreReadScope.launch {
            App.INSTANCE.dataStore.data.collect {
                latestDataStorePreferences = it
            }
        }
    }
}

private fun DataStore<Preferences>.currentPreferences(): Preferences {
    ensureDataStoreSnapshot()
    latestDataStorePreferences?.let { return it }
    return runBlocking { data.first() }.also {
        latestDataStorePreferences = it
    }
}

@Composable
fun <T> rememberPreferenceAsMutableState(
    key: Preferences.Key<T>,
    defaultValue: T
): MutableState<T> {
    val dataStore = LocalContext.current.dataStore
    val state = remember { mutableStateOf(defaultValue) }

    LaunchedEffect(Unit) {
        dataStore.data.map { it[key] ?: defaultValue }.distinctUntilChanged()
            .collect { state.value = it }
    }

    LaunchedEffect(state.value) {
        dataStore.edit { it[key] = state.value }
    }

    return state
}

@Composable
fun <T> rememberPreferenceAsState(
    key: Preferences.Key<T>,
    defaultValue: T
): State<T> {
    val dataStore = LocalContext.current.dataStore
    val state = remember { mutableStateOf(defaultValue) }

    LaunchedEffect(Unit) {
        dataStore.data.map { it[key] ?: defaultValue }.distinctUntilChanged()
            .collect { state.value = it }
    }

    LaunchedEffect(state.value) {
        dataStore.edit { it[key] = state.value }
    }

    return state
}

@SuppressLint("FlowOperatorInvokedInComposition")
@Composable
fun <T> DataStore<Preferences>.collectPreferenceAsState(
    key: Preferences.Key<T>,
    defaultValue: T
): State<T> {
    return data.map { it[key] ?: defaultValue }.collectAsState(initial = defaultValue)
}

fun DataStore<Preferences>.putString(key: String, value: String? = null) {
    dataStoreWriteScope.launch {
        edit {
            if (value == null) {
                it.remove(stringPreferencesKey(key))
            } else {
                it[stringPreferencesKey(key)] = value
            }
            latestDataStorePreferences = it.toPreferences()
        }
    }
}

fun DataStore<Preferences>.putBoolean(key: String, value: Boolean) {
    dataStoreWriteScope.launch {
        edit {
            it[booleanPreferencesKey(key)] = value
            latestDataStorePreferences = it.toPreferences()
        }
    }
}

fun DataStore<Preferences>.putInt(key: String, value: Int) {
    dataStoreWriteScope.launch {
        edit {
            it[intPreferencesKey(key)] = value
            latestDataStorePreferences = it.toPreferences()
        }
    }
}

fun DataStore<Preferences>.getInt(key: String, defaultValue: Int): Int {
    return currentPreferences()[intPreferencesKey(key)] ?: defaultValue
}

fun DataStore<Preferences>.getString(key: String): String? {
    return currentPreferences()[stringPreferencesKey(key)]
}

fun DataStore<Preferences>.getString(key: String, defaultValue: String): String {
    return currentPreferences()[stringPreferencesKey(key)] ?: defaultValue
}

fun DataStore<Preferences>.getStringSet(
    key: String,
    defaultValues: MutableSet<String>? = null
): MutableSet<String>? {
    return currentPreferences()[stringSetPreferencesKey(key)]?.toMutableSet() ?: defaultValues
}

fun DataStore<Preferences>.getBoolean(key: String, defaultValue: Boolean): Boolean {
    return currentPreferences()[booleanPreferencesKey(key)] ?: defaultValue
}

fun DataStore<Preferences>.getFloat(key: String, defaultValue: Float): Float {
    return currentPreferences()[floatPreferencesKey(key)] ?: defaultValue
}

fun DataStore<Preferences>.getLong(key: String, defaultValue: Long): Long {
    return currentPreferences()[longPreferencesKey(key)] ?: defaultValue
}

class DataStorePreference : PreferenceDataStore() {
    override fun putString(key: String, value: String?) {
        dataStoreWriteScope.launch {
            App.INSTANCE.dataStore.edit {
                if (value == null) {
                    it.remove(stringPreferencesKey(key))
                } else {
                    it[stringPreferencesKey(key)] = value
                }
                latestDataStorePreferences = it.toPreferences()
            }
        }
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        dataStoreWriteScope.launch {
            App.INSTANCE.dataStore.edit {
                if (values == null) {
                    it.remove(stringSetPreferencesKey(key))
                } else {
                    it[stringSetPreferencesKey(key)] = values
                }
                latestDataStorePreferences = it.toPreferences()
            }
        }
    }

    override fun putInt(key: String, value: Int) {
        dataStoreWriteScope.launch {
            App.INSTANCE.dataStore.edit {
                it[intPreferencesKey(key)] = value
                latestDataStorePreferences = it.toPreferences()
            }
        }
    }

    override fun putLong(key: String, value: Long) {
        dataStoreWriteScope.launch {
            App.INSTANCE.dataStore.edit {
                it[longPreferencesKey(key)] = value
                latestDataStorePreferences = it.toPreferences()
            }
        }
    }

    override fun putFloat(key: String, value: Float) {
        dataStoreWriteScope.launch {
            App.INSTANCE.dataStore.edit {
                it[floatPreferencesKey(key)] = value
                latestDataStorePreferences = it.toPreferences()
            }
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        dataStoreWriteScope.launch {
            App.INSTANCE.dataStore.edit {
                it[booleanPreferencesKey(key)] = value
                latestDataStorePreferences = it.toPreferences()
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        return App.INSTANCE.dataStore.getString(key) ?: defValue
    }

    override fun getStringSet(
        key: String,
        defValues: MutableSet<String>?
    ): MutableSet<String>? {
        return App.INSTANCE.dataStore.getStringSet(key, defValues)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return App.INSTANCE.dataStore.getInt(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return App.INSTANCE.dataStore.getLong(key, defValue)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return App.INSTANCE.dataStore.getFloat(key, defValue)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return App.INSTANCE.dataStore.getBoolean(key, defValue)
    }
}
