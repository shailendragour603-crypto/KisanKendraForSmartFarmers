package shop.sabjiwala.kisankendra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MandiViewModelFactory(private val repository: MandiRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MandiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MandiViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}