package com.huanchengfly.tieba.post.ui.common.prefs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.contentCard

lateinit var LocalPrefsDataStore: ProvidableCompositionLocal<DataStore<Preferences>>

/**
 * Main preference screen which holds [PrefsListItem]s
 *
 * @param dataStore DataStore which will be used to save all the preferences
 * @param modifier Modifier applied to the [LazyColumn] holding the list of Prefs
 *
 */
@Composable
fun PrefsScreen(
    dataStore: DataStore<Preferences>,
    modifier: Modifier = Modifier,
    dividerThickness: Dp = 1.dp, // 0 for no divider
    dividerIndent: Dp = 0.dp, // indents on both sides
    content: PrefsScope.() -> Unit
) {
    LocalPrefsDataStore = staticCompositionLocalOf { dataStore }
    val prefsScope = PrefsScopeImpl().apply(content)

    // Now the dataStore can be accessed by calling LocalPrefsDataStore.current from any child Pref
    CompositionLocalProvider(LocalPrefsDataStore provides dataStore) {
        Container {
            Column {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
                    ),
                ) {
                    items(prefsScope.prefsItems.size) { index ->
                        // Footer metadata includes the last real preference and the following spacer.
                        val isGroupSpacer = index in prefsScope.footerIndexes && index - 1 in prefsScope.footerIndexes
                        if (index in prefsScope.headerIndexes || isGroupSpacer) {
                            prefsScope.getPrefsItem(index)()
                        } else {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).contentCard()
                            ) {
                                prefsScope.getPrefsItem(index)()
                            }
                        }

                        if (dividerThickness != 0.dp
                            && index != prefsScope.prefsItems.size - 1
                            && !prefsScope.headerIndexes.contains(index)
                            && !prefsScope.headerIndexes.contains(index + 1)
                            && !prefsScope.footerIndexes.contains(index)
                        ) {
                            Divider(
                                thickness = dividerThickness,
                                indent = dividerIndent
                            )
                        }
                    }
                }
            }
        }
    }
}
