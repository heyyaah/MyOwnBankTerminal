package com.myownbank.terminal

import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class AppDisabledActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_disabled)

        val newsLink = intent.getStringExtra("newsLink") ?: "t.me/kotakbaslife"

        findViewById<TextView>(R.id.disabledText).text = "ПРИЛОЖЕНИЕ ОТКЛЮЧЕНО!"
        findViewById<TextView>(R.id.newsText).text = "Дождитесь включения! Новости: $newsLink"

        // ----------------------------------------
        // Блокируем кнопку назад
        // ----------------------------------------
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Пусто → жест назад игнорируется
            }
        })
    }
}