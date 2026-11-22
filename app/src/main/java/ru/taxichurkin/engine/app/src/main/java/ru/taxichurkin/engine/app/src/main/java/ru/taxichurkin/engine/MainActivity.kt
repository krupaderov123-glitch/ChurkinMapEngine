package ru.taxichurkin.engine

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запускаем наш собственный картографический движок
        val mapEngine = ChurkinMapEngine(this)

        setContentView(mapEngine)
    }
}
