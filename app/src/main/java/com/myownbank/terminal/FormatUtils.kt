package com.myownbank.terminal

import java.text.NumberFormat
import java.util.Locale

/**
 * Форматирует сумму в копейках в строку вида "1 500,99 ₽"
 * Если копейки = 00, показывает "1 500 ₽"
 */
fun formatPrice(kopecks: Long): String {
    val rubles = kopecks / 100
    val cents = kopecks % 100
    val nf = NumberFormat.getNumberInstance(Locale("ru", "RU"))
    return if (cents == 0L) {
        "${nf.format(rubles)} ₽"
    } else {
        "${nf.format(rubles)},${cents.toString().padStart(2, '0')} ₽"
    }
}
