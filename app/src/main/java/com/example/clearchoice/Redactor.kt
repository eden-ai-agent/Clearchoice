package com.example.clearchoice

import java.util.regex.Pattern

object Redactor {

    // Regex for matching common email formats
    private val EMAIL_REGEX = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                "\\@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+"
    )

    // Regex for matching various phone number formats
    // Supports formats like: (123) 456-7890, 123-456-7890, 123.456.7890, 1234567890, +1 123 456 7890, etc.
    private val PHONE_REGEX = Pattern.compile(
        "(\\+\\d{1,3}[\\s-]?)?" + // Optional country code +1, +44 etc.
                "((\\(\\d{3}\\))|\\d{3})" + // Area code in parentheses or not
                "[\\s.-]?" +       // Separator
                "\\d{3}" +         // First 3 digits
                "[\\s.-]?" +       // Separator
                "\\d{4}"           // Last 4 digits
    )

    // Placeholder/Simple Regex for Names: Matches capitalized words.
    // This is a known limitation and NOT a secure/reliable way to redact all names.
    // It's provided to fulfill the "regex-based" requirement for names with a simple approach.
    // A more robust solution would require NLP techniques.
    private val NAME_REGEX = Pattern.compile(
        "\\b[A-Z][a-z]+(?:\\s[A-Z][a-z]+)*\\b"
    )


    fun redact(text: String): String {
        var redactedText = text

        // Redact Emails
        redactedText = EMAIL_REGEX.matcher(redactedText).replaceAll("[REDACTED_EMAIL]")

        // Redact Phone Numbers
        redactedText = PHONE_REGEX.matcher(redactedText).replaceAll("[REDACTED_PHONE]")

        // Redact Names (Simple Placeholder)
        // This will redact any sequence of capitalized words.
        // It might over-redact (e.g., start of sentences, acronyms if not handled)
        // or under-redact (e.g., names not fitting this simple pattern).
        redactedText = NAME_REGEX.matcher(redactedText).replaceAll("[REDACTED_NAME]")

        return redactedText
    }
}
