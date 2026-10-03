package com.fieldconsole.ui.search

import androidx.navigation.NavController
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.viewmodel.MainViewModel

/** Reuse the existing main destination and its ViewModel; do not insert another scan. */
internal fun openSearchEntry(nav: NavController, mainVm: MainViewModel, entry: FieldEntry) {
    mainVm.openEntry(entry)
    nav.popBackStack("main", inclusive = false)
}
