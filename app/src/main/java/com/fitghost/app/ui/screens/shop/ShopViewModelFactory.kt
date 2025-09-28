package com.fitghost.app.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.fitghost.app.data.repository.CartRepositoryProvider
import com.fitghost.app.data.repository.ShopRepositoryImpl
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory

/**
 * ShopViewModel Factory
 * 간단한 DI 없이 ViewModel 생성
 */
class ShopViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(ShopViewModel::class.java)) {
            val application = extras[AndroidViewModelFactory.APPLICATION_KEY]!!
            return ShopViewModel(
                shopRepository = ShopRepositoryImpl(application.applicationContext),
                cartRepository = CartRepositoryProvider.instance
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}