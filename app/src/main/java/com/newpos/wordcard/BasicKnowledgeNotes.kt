package com.newpos.wordcard

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal class BasicKnowledgeNotes(private val context: Context) {
    private val directory = context.filesDir

    fun files(): List<File> {
        for (assetName in context.assets.list("").orEmpty()) {
            if (!assetName.endsWith(".md", ignoreCase = true)) continue
            val bundledFile = File(directory, assetName)
            if (bundledFile.exists()) continue

            val markdown = context.assets.open(assetName).bufferedReader().use { it.readText() }
            write(bundledFile, markdown)
        }
        return directory.listFiles { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
            ?.sortedBy { it.name } ?: throw IOException("Cannot list Markdown notes")
    }

    fun preview(file: File): String = try {
        file.bufferedReader().use { title(it.readLine().orEmpty()) }
    } catch (e: IOException) {
        file.nameWithoutExtension
    }

    fun read(fileName: String): String = File(directory, fileName).readText()

    fun save(fileName: String?, markdown: String) {
        val file = if (fileName == null) newFile(markdown) else File(directory, fileName)
        write(file, markdown)
    }

    private fun title(firstLine: String): String = firstLine.replace("#", "").trim()
        .ifBlank { context.getString(R.string.markdown_untitled) }

    private fun newFile(markdown: String): File {
        val safeTitle = title(markdown.lineSequence().first())
            .replace(Regex("""[/\\:*?"<>|\p{Cntrl}]"""), "_")
        // Leave room for the suffix and extension within the filesystem's byte limit.
        val end = safeTitle.offsetByCodePoints(0, safeTitle.codePointCount(0, safeTitle.length).coerceAtMost(58))
        val stem = safeTitle.substring(0, end).trimEnd()
        var file = File(directory, "$stem.md")
        var number = 2
        while (file.exists()) {
            file = File(directory, "$stem ($number).md")
            number++
        }
        return file
    }

    private fun write(file: File, markdown: String) {
        val tempFile = File.createTempFile("note-", ".tmp", directory)
        try {
            tempFile.writeText(markdown)
            Files.move(tempFile.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            tempFile.delete()
        }
    }
}
