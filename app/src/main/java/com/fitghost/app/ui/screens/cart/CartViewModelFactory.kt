package com.fitghost.app.ui.screens.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.fitghost.app.data.repository.CartRepositoryProvider

/**
 * CartViewModel Factory
 * 간단한 DI 없이 ViewModel 생성
 */
class CartViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(CartViewModel::class.java)) {
            return CartViewModel(
                cartRepository = CartRepositoryProvider.instance
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}