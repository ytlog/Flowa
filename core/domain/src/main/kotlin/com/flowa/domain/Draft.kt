package com.flowa.domain

/** Revision distinguishes a new edit from an older draft with identical text. */
data class Draft(
    val text: String = "",
    val tags: List<String> = emptyList(),
    val revision: Long = 0,
) {
    fun edit(text: String = this.text, tags: List<String> = this.tags): Draft {
        val distinctTags = tags.distinct()
        return if (text == this.text && distinctTags == this.tags) this
        else Draft(text, distinctTags, revision + 1)
    }

    fun clear(keepTags: Boolean = false): Draft =
        Draft(tags = if (keepTags) tags else emptyList(), revision = revision + 1)

    fun restore(previous: Draft): Draft = previous.copy(revision = revision + 1)
}
