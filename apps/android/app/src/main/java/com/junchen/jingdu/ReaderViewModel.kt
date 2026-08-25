package com.junchen.jingdu

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Screen-level state owner for the Compose reader/application surface. MainActivity remains the
 * Android integration host (SAF, billing and service intents); UI state follows UDF through here.
 */
internal class ReaderViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    fun replace(value: AppUiState) { mutableState.value = value }
    fun reduce(block: (AppUiState) -> AppUiState) { mutableState.update(block) }
}
