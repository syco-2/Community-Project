package com.sixmonths.app.ui.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Manual DI for ViewModels: no Hilt, just a small factory. */
fun <VM : ViewModel> viewModelFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
