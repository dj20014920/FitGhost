package com.fitghost.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fitghost.app.data.CreditStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class CreditStoreInstrumentedTest {
    @Test
    fun testConsumeAndBonus() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = CreditStore(ctx)
        // 초기 상태 가져오기
        val s1 = store.state()
        var latest = CreditStore.State("", 0, 0)
        val job = CoroutineScope(Dispatchers.Main).launch { 
            s1.collect { latest = it; cancel() } 
        }
        job.join()
        // 11회 사용 시 10회는 used, 이후는 bonus 감소
        repeat(10) { store.consumeOne() }
        val s2Flow = store.state()
        var after = latest
        val job2 = CoroutineScope(Dispatchers.Main).launch { 
            s2Flow.collect { after = it; cancel() } 
        }
        job2.join()
        assertEquals(10, after.used)
        store.addBonusOne()
        val job3 = CoroutineScope(Dispatchers.Main).launch { 
            s2Flow.collect { after = it; cancel() } 
        }
        job3.join()
        assertEquals(1, after.bonus)
    }
}