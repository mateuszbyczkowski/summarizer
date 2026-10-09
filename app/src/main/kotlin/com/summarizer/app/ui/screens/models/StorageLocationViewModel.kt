package com.summarizer.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summarizer.app.domain.repository.PreferencesRepository
import com.summarizer.app.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageLocationViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /** The saved location, or null until loaded. */
    private val _savedLocation = MutableStateFlow<StorageHelper.StorageLocation?>(null)
    val savedLocation: StateFlow<StorageHelper.StorageLocation?> = _savedLocation.asStateFlow()

    init {
        viewModelScope.launch {
            _savedLocation.value = preferencesRepository.getStorageLocation()
        }
    }

    fun saveLocation(location: StorageHelper.StorageLocation, onSaved: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setStorageLocation(location)
            _savedLocation.value = location
            onSaved()
        }
    }
}
