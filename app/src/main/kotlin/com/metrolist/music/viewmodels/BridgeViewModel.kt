/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import com.metrolist.music.ui.screens.bridge.BridgeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BridgeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<BridgeUiState>(BridgeUiState.Idle)
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()
}
