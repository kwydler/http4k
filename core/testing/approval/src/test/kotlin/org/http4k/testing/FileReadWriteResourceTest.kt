package org.http4k.testing

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import kotlin.io.path.createTempDirectory
import kotlin.random.Random

class FileReadWriteResourceTest {

    private val random = SecureRandom()
    private val tempDir = createTempDirectory("http4k-test").toFile()

    @Test
    fun `non existent file cannot be read`() {
        val target = File(tempDir, javaClass.name + random.nextLong().toString())
        assertThat(FileReadWriteResource(target).input(), absent())
        target.delete()
    }

    @Test
    fun `non existent file can be written to`() {
        val target = File(tempDir, javaClass.name + random.nextLong().toString())
        with(FileReadWriteResource(target)) {
            output().writer().use { it.write("goodbye") }
            assertThat(input()!!.reader().use { it.readText() }, equalTo("goodbye"))
        }
        target.delete()
    }

    @Test
    fun `existing file can be read`() {
        val file = FileReadWriteResource(existingFile("hello"))
        assertThat(file.input()!!.reader().use { it.readText() }, equalTo("hello"))
    }

    @Test
    fun `existing file can be written to`() {
        with(FileReadWriteResource(existingFile("hello"))) {
            output().writer().use { it.write("goodbye") }
            assertThat(input()!!.reader().use { it.readText() }, equalTo("goodbye"))
        }
    }

    private fun existingFile(content: String) = Files.createTempFile(javaClass.name, ".tmp").toFile().apply {
        writeText(content)
    }
}
