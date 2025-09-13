package com.fitghost.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitghost.app.util.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine

class HomeViewModel(app: Application): AndroidViewModel(app) {
    private val repo = ServiceLocator.weatherRepo()
    private val db = ServiceLocator.db(app)
    val recommender = ServiceLocator.recommender

    // 실제 위치는 후속 구현. MVP로는 하드코딩(서울).
    val weather = kotlinx.coroutines.flow.flow { emit(repo.today(37.5665, 126.9780)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 옷장 로딩(Flow)
    val wardrobe = db.wardrobeDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // TOP3 코디 추천(날씨+옷장 결합)
    val top3 = combine(weather, wardrobe) { w, clothes ->
        if (w == null) emptyList() else recommender.recommend(clothes, w, topN = 3)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
