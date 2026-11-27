package com.mediahub.rebuilt.keyboard

/**
 * Recipient-visible text treatments for GRIMY BOARD.
 * These are Unicode characters, not embedded fonts: recipients do not need this app.
 */
object GrimyTextStyles {
    data class Style(val id: String, val name: String, val sample: String)

    val styles = listOf(
        Style("normal", "Normal", "Hi"),
        Style("grimy", "GRIMY SIGNATURE", "ᴴⁱ"),
        Style("bold", "Bold", "𝐇𝐢"),
        Style("italic", "Italic", "𝐻𝑖"),
        Style("bold_italic", "Bold Italic", "𝑯𝒊"),
        Style("script", "Royal Script", "𝓗𝓲"),
        Style("bold_script", "Bold Script", "𝓗𝓲"),
        Style("fraktur", "Gothic", "ℌ𝔦"),
        Style("bold_fraktur", "Blackletter Bold", "𝕳𝖎"),
        Style("double", "Double Struck", "ℍ𝕚"),
        Style("mono", "Monospace", "𝙷𝚒"),
        Style("sans", "Modern Sans", "𝖧𝗂"),
        Style("sans_bold", "Sans Bold", "𝗛𝗶"),
        Style("sans_italic", "Sans Italic", "𝘏𝘪"),
        Style("sans_bold_italic", "Sans Bold Italic", "𝙃𝙞"),
        Style("circled", "Circled", "Ⓗⓘ"),
        Style("squared", "Squared", "🄷🄸"),
        Style("fullwidth", "Fullwidth", "Ｈｉ")
    )

    fun apply(text: String, styleId: String): String {
        if (styleId == "normal" || text.isEmpty()) return text
        val upperStart: Int
        val lowerStart: Int
        when (styleId) {
            "bold" -> { upperStart = 0x1D400; lowerStart = 0x1D41A }
            "italic" -> { upperStart = 0x1D434; lowerStart = 0x1D44E }
            "bold_italic" -> { upperStart = 0x1D468; lowerStart = 0x1D482 }
            "script" -> { upperStart = 0x1D49C; lowerStart = 0x1D4B6 }
            "bold_script" -> { upperStart = 0x1D4D0; lowerStart = 0x1D4EA }
            "fraktur" -> { upperStart = 0x1D504; lowerStart = 0x1D51E }
            "bold_fraktur" -> { upperStart = 0x1D56C; lowerStart = 0x1D586 }
            "double" -> { upperStart = 0x1D538; lowerStart = 0x1D552 }
            "mono" -> { upperStart = 0x1D670; lowerStart = 0x1D68A }
            "sans" -> { upperStart = 0x1D5A0; lowerStart = 0x1D5BA }
            "sans_bold" -> { upperStart = 0x1D5D4; lowerStart = 0x1D5EE }
            "sans_italic" -> { upperStart = 0x1D608; lowerStart = 0x1D622 }
            "sans_bold_italic" -> { upperStart = 0x1D63C; lowerStart = 0x1D656 }
            else -> { upperStart = 0; lowerStart = 0 }
        }
        if (styleId == "grimy") {
            val smallCaps = mapOf('a' to 'ᴀ','b' to 'ʙ','c' to 'ᴄ','d' to 'ᴅ','e' to 'ᴇ','f' to 'ꜰ','g' to 'ɢ','h' to 'ʜ','i' to 'ɪ','j' to 'ᴊ','k' to 'ᴋ','l' to 'ʟ','m' to 'ᴍ','n' to 'ɴ','o' to 'ᴏ','p' to 'ᴘ','q' to 'ǫ','r' to 'ʀ','s' to 's','t' to 'ᴛ','u' to 'ᴜ','v' to 'ᴠ','w' to 'ᴡ','x' to 'x','y' to 'ʏ','z' to 'ᴢ')
            return text.map { ch -> if (ch.isLowerCase()) smallCaps[ch] ?: ch else if (ch.isUpperCase()) smallCaps[ch.lowercaseChar()] ?: ch else ch }.joinToString("")
        }
        if (styleId == "circled") return text.map { ch ->
            when (ch) {
                in 'A'..'Z' -> String(Character.toChars(0x24B6 + ch.code - 65))
                in 'a'..'z' -> String(Character.toChars(0x24D0 + ch.code - 97))
                else -> ch.toString()
            }
        }.joinToString("")
        if (styleId == "squared") return text.map { ch ->
            when (ch) {
                in 'A'..'Z' -> String(Character.toChars(0x1F130 + ch.code - 65))
                in 'a'..'z' -> String(Character.toChars(0x1F130 + ch.code - 97))
                else -> ch.toString()
            }
        }.joinToString("")
        if (styleId == "fullwidth") return text.map { ch ->
            when (ch) { in '!'..'~' -> String(Character.toChars(ch.code + 0xFEE0)); ' ' -> "　"; else -> ch.toString() }
        }.joinToString("")
        return buildString {
            text.forEach { ch ->
                val mapped = when (ch) {
                    in 'A'..'Z' -> upperStart.takeIf { it != 0 }?.plus(ch.code - 65)
                    in 'a'..'z' -> lowerStart.takeIf { it != 0 }?.plus(ch.code - 97)
                    in '0'..'9' -> when (styleId) {
                        "bold" -> 0x1D7CE + ch.code - 48
                        "double" -> 0x1D7D8 + ch.code - 48
                        "mono" -> 0x1D7F6 + ch.code - 48
                        "sans_bold" -> 0x1D7EC + ch.code - 48
                        else -> null
                    }
                    else -> null
                }
                if (mapped != null) append(String(Character.toChars(mapped))) else append(ch)
            }
        }
    }
}
