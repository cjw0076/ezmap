package com.example.ez_capstone.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensitiveDataMasker @Inject constructor() {

    private val phonePattern = Regex("""01[0-9]-?\d{3,4}-?\d{4}""")
    private val cardPattern = Regex("""\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}""")
    private val rrnPattern = Regex("""\d{6}-\d{7}""")

    fun mask(text: String): String = text
        .replace(phonePattern) { mr ->
            val digits = mr.value.filter { it.isDigit() }
            "${digits.take(3)}-****-${digits.takeLast(4)}"
        }
        .replace(cardPattern) { mr ->
            val parts = mr.value.split(Regex("[-\\s]"))
            val masked = if (parts.size >= 2) {
                "${parts.firstOrNull() ?: "****"}-****-****-${parts.lastOrNull() ?: "****"}"
            } else {
                // 구분자 없는 연속 16자리: 앞 4자리만 노출
                val raw = mr.value.replace(Regex("[-\\s]"), "")
                "${raw.take(4)}-****-****-****"
            }
            masked
        }
        .replace(rrnPattern, "******-*******")

    fun maskAll(texts: List<String>): List<String> = texts.map { mask(it) }
}
