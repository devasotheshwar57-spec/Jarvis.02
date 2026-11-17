package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarvis", appName)
    }

    @Test
    fun `calculator evaluator handles complex expressions`() {
        val res = ExpressionEvaluator.evaluate("2*(3+4)^2 - sqrt(16)")
        assertTrue(res.isSuccess)
        assertEquals("94", ExpressionEvaluator.formatResult(res.getOrThrow()))
    }
}
