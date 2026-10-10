package com.summarizer.app.domain.ai

private val UNCLOSED_THINKING = Regex("<think>[\\s\\S]*$")

/**
 * Removes model reasoning from a reply, including a reasoning block cut off before it closed.
 * Shared by the on-device engines.
 */
fun stripReasoning(text: String): String {
    var answer = text
    if ("</think>" in answer) answer = answer.substringAfterLast("</think>")
    return answer.replace(UNCLOSED_THINKING, "").trim()
}
