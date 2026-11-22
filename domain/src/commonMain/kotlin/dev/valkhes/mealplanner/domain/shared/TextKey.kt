package dev.valkhes.mealplanner.domain.shared

/**
 * A comparison key for names typed by people: case, accents and extra spaces do not matter, so
 * "Gratin Dauphinois", "gratin  dauphinois" and "gratin dauphinoïs" are the same dish.
 */
fun textKey(text: String): String {
    val builder = StringBuilder(text.length)
    var lastWasSpace = true
    for (char in text.lowercase()) {
        if (char.isWhitespace()) {
            if (!lastWasSpace) builder.append(' ')
            lastWasSpace = true
            continue
        }
        builder.append(ACCENTS[char] ?: char.toString())
        lastWasSpace = false
    }
    return builder.toString().trimEnd()
}

private val ACCENTS: Map<Char, String> =
    buildMap {
        "àáâãäå".forEach { put(it, "a") }
        "çćč".forEach { put(it, "c") }
        "èéêë".forEach { put(it, "e") }
        "ìíîï".forEach { put(it, "i") }
        "ñ".forEach { put(it, "n") }
        "òóôõöø".forEach { put(it, "o") }
        "ùúûü".forEach { put(it, "u") }
        "ýÿ".forEach { put(it, "y") }
        put('œ', "oe")
        put('æ', "ae")
        put('ß', "ss")
        put('’', "'")
    }
