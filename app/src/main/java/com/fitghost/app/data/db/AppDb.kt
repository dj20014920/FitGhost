package com.fitghost.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.data.model.Garment

@Database(entities = [Garment::class, CartItem::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun cartDao(): CartDao
}
