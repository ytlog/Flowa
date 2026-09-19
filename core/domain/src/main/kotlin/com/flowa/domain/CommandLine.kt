package com.flowa.domain

/** Small shell-like tokenizer: data only, never evaluated as a shell command. */
object CommandLine {
    fun tokenize(raw: String): List<String> {
        val words = mutableListOf<String>(); val current = StringBuilder()
        var quote: Char? = null; var escaped = false; var started = false
        raw.forEach { char ->
            when {
                escaped -> { current.append(char); escaped = false; started = true }
                char == '\\' && quote != '\'' -> { escaped = true; started = true }
                quote != null -> if (char == quote) quote = null else current.append(char)
                char == '\'' || char == '"' -> { quote = char; started = true }
                char.isWhitespace() -> if (started) { words.add(current.toString()); current.clear(); started = false }
                else -> { current.append(char); started = true }
            }
        }
        require(quote == null && !escaped) { "命令中的引号或转义不完整。" }
        if (started) words.add(current.toString())
        return words
    }
    fun options(words: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>(); var i = 1
        while (i < words.size) {
            val key = words[i]
            if (key.startsWith("--")) {
                val value = words.getOrNull(i + 1)
                if (value != null && !value.startsWith("--")) { result[key.removePrefix("--")] = value; i++ } else result[key.removePrefix("--")] = "true"
            }
            i++
        }
        return result
    }
}
