package org.http4k.format

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Sets the `line.separator` system property to the line feed (LF) character before all tests to ensure that different
 * platforms output the same string. This is only needed when a test compares prettified output to an expected string.
 */
class SetLineSeparatorExtension : BeforeAllCallback, AfterAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        store(context).put(LINE_SEPARATOR_KEY, System.getProperty(LINE_SEPARATOR_KEY))
        System.setProperty(LINE_SEPARATOR_KEY, LINE_SEPARATOR)
    }

    override fun afterAll(context: ExtensionContext) {
        val original = store(context).remove(LINE_SEPARATOR_KEY, String::class.java)

        if (original == null) {
            System.clearProperty(LINE_SEPARATOR_KEY)
        } else {
            System.setProperty(LINE_SEPARATOR_KEY, original)
        }
    }

    private fun store(context: ExtensionContext): ExtensionContext.Store {
        return context.getStore(ExtensionContext.Namespace.create(javaClass, context.requiredTestClass))
    }

    companion object {
        private const val LINE_SEPARATOR = "\n"
        private const val LINE_SEPARATOR_KEY = "line.separator"
    }
}
