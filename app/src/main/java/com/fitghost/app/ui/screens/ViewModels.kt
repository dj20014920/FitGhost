package com.fitghost.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitghost.app.util.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

class HomeViewModel(app: Application): AndroidViewModel(app) {
    private val repo = ServiceLocator.weatherRepo()
    private val db = ServiceLocator.db(app)
    val recommender = ServiceLocator.recommender

    // 초기 버전: 현재 위치를 Activity에서 전달받아 저장(없으면 서울 기본값)
    private val _location = kotlinx.coroutines.flow.MutableStateFlow(37.5665 to 126.9780)
    fun setLocation(lat: Double, lon: Double) { _location.value = lat to lon }

    val weather = _location.flatMapLatest { (lat, lon) ->
        kotlinx.coroutines.flow.flow { emit(repo.today(lat, lon)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 옷장 로딩(Flow)
    val wardrobe = db.wardrobeDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // TOP3 코디 추천(날씨+옷장 결합)
    val top3 = combine(weather, wardrobe) { w, clothes ->
        if (w == null) emptyList() else recommender.recommend(clothes, w, topN = 3)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
