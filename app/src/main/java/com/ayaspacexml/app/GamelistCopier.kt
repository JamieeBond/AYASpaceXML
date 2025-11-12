package com.ayaspacexml.app

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter

object GamelistCopier {
    private const val TAG = "GamelistCopier"

    suspend fun copyGamelists(
        context: Context,
        fromPathUri: String,
        toPathUri: String
    ) = withContext(Dispatchers.IO) {
        try {
            val fromDocumentFile = DocumentFile.fromTreeUri(context, fromPathUri.toUri())
            if (fromDocumentFile == null) {
                Log.e(TAG, "Failed to open source directory: $fromPathUri")
                return@withContext
            }

            val toDocumentFile = DocumentFile.fromTreeUri(context, toPathUri.toUri())
            if (toDocumentFile == null) {
                Log.e(TAG, "Failed to open destination directory: $toPathUri")
                return@withContext
            }

            val gamelistsDir = fromDocumentFile.findFile("gamelists")
            if (gamelistsDir == null) {
                Log.e(TAG, "gamelists directory not found in source")
                return@withContext
            }

            val downloadedMediaDir = fromDocumentFile.findFile("downloaded_media")
            if (downloadedMediaDir == null) {
                Log.w(TAG, "downloaded_media directory not found in source")
            }

            val systemDirs = gamelistsDir.listFiles()
            Log.d(TAG, "Found ${systemDirs.size} system directories to process")

            systemDirs.forEach { systemDir ->
                if (systemDir.isDirectory) {
                    val dirName = systemDir.name ?: "unknown"
                    Log.d(TAG, "Processing system directory: $dirName")
                    try {
                        processSystemDirectory(context, systemDir, toDocumentFile, downloadedMediaDir)
                        Log.d(TAG, "Successfully processed: $dirName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing system directory $dirName", e)
                    }
                }
            }

            Log.d(TAG, "Finished processing all system directories")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in copyGamelists", e)
        }
    }

    private fun processSystemDirectory(
        context: Context,
        systemDir: DocumentFile,
        toDocumentFile: DocumentFile,
        downloadedMediaDir: DocumentFile?
    ) {
        val dirName = systemDir.name
        if (dirName == null) {
            Log.e(TAG, "System directory has no name")
            return
        }

        val gamelistFile = systemDir.findFile("gamelist.xml")
        if (gamelistFile?.isFile != true) {
            Log.w(TAG, "No gamelist.xml found in $dirName")
            return
        }

        Log.d(TAG, "Found gamelist.xml in $dirName")

        // Find or create destination system directory
        var toSystemDir = toDocumentFile.findFile(dirName)
        if (toSystemDir == null) {
            Log.d(TAG, "Creating system directory: $dirName")
            toSystemDir = toDocumentFile.createDirectory(dirName)
            if (toSystemDir == null) {
                Log.e(TAG, "Failed to create system directory: $dirName")
                return
            }
        }

        // Clear existing media folders
        clearMediaFolders(toSystemDir)

        // Process media if available
        val mediaSystemDir = downloadedMediaDir?.findFile(dirName)
        if (mediaSystemDir != null) {
            Log.d(TAG, "Found media directory for $dirName")
            processGamelistWithMedia(context, gamelistFile, toSystemDir, mediaSystemDir)
        } else {
            Log.w(TAG, "No media directory found for $dirName")
            // Still copy the gamelist even without media
            copyGamelistOnly(context, gamelistFile, toSystemDir)
        }
    }

    private fun clearMediaFolders(systemDir: DocumentFile) {
        try {
            val mediaDir = systemDir.findFile("media")
            if (mediaDir != null) {
                Log.d(TAG, "Clearing existing media folders in ${systemDir.name}")

                mediaDir.findFile("image")?.listFiles()?.forEach {
                    if (!it.delete()) {
                        Log.w(TAG, "Failed to delete image: ${it.name}")
                    }
                }

                mediaDir.findFile("thumbnail")?.listFiles()?.forEach {
                    if (!it.delete()) {
                        Log.w(TAG, "Failed to delete thumbnail: ${it.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing media folders", e)
        }
    }

    private fun copyGamelistOnly(
        context: Context,
        gamelistFile: DocumentFile,
        toSystemDir: DocumentFile
    ) {
        try {
            val content = context.contentResolver.openInputStream(gamelistFile.uri)?.use {
                it.reader().readText()
            }

            if (content == null) {
                Log.e(TAG, "Failed to read gamelist content")
                return
            }

            writeGamelist(context, toSystemDir, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error copying gamelist", e)
        }
    }

    private fun processGamelistWithMedia(
        context: Context,
        gamelistFile: DocumentFile,
        toSystemDir: DocumentFile,
        mediaSystemDir: DocumentFile
    ) {
        // Ensure media directory structure exists
        var mediaDir = toSystemDir.findFile("media")
        if (mediaDir == null) {
            mediaDir = toSystemDir.createDirectory("media")
            if (mediaDir == null) {
                Log.e(TAG, "Failed to create media directory")
                copyGamelistOnly(context, gamelistFile, toSystemDir)
                return
            }
        }

        var imageDir = mediaDir.findFile("image")
        if (imageDir == null) {
            imageDir = mediaDir.createDirectory("image")
            if (imageDir == null) {
                Log.e(TAG, "Failed to create image directory")
                copyGamelistOnly(context, gamelistFile, toSystemDir)
                return
            }
        }

        var thumbnailDir = mediaDir.findFile("thumbnail")
        if (thumbnailDir == null) {
            thumbnailDir = mediaDir.createDirectory("thumbnail")
            if (thumbnailDir == null) {
                Log.e(TAG, "Failed to create thumbnail directory")
                copyGamelistOnly(context, gamelistFile, toSystemDir)
                return
            }
        }

        val coversDir = mediaSystemDir.findFile("covers")
        val fanartDir = mediaSystemDir.findFile("fanart")
        val screenshotsDir = mediaSystemDir.findFile("screenshots")

        Log.d(TAG, "Media directories - covers: ${coversDir != null}, fanart: ${fanartDir != null}, screenshots: ${screenshotsDir != null}")

        val modifiedXml = parseAndEnrichGamelist(
            context,
            gamelistFile,
            coversDir,
            fanartDir,
            screenshotsDir,
            imageDir,
            thumbnailDir
        )

        if (modifiedXml == null) {
            Log.e(TAG, "Failed to parse and enrich gamelist")
            copyGamelistOnly(context, gamelistFile, toSystemDir)
            return
        }

        writeGamelist(context, toSystemDir, modifiedXml)
    }

    private fun parseAndEnrichGamelist(
        context: Context,
        gamelistFile: DocumentFile,
        coversDir: DocumentFile?,
        fanartDir: DocumentFile?,
        screenshotsDir: DocumentFile?,
        imageDir: DocumentFile,
        thumbnailDir: DocumentFile
    ): String? {
        return try {
            val parser = Xml.newPullParser()
            val stringWriter = StringWriter()
            val serializer = Xml.newSerializer()

            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
            serializer.setOutput(stringWriter)

            val originalXml = context.contentResolver.openInputStream(gamelistFile.uri)?.use {
                it.reader().readText()
            }

            if (originalXml == null) {
                Log.e(TAG, "Failed to read original XML")
                return null
            }

            parser.setInput(originalXml.reader())
            serializer.startDocument("UTF-8", true)

            var eventType = parser.eventType
            var inGameTag = false
            var gamePath: String? = null
            var currentTag: String? = null
            var gamesProcessed = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_DOCUMENT -> {
                        // Already handled by serializer.startDocument
                    }

                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        serializer.startTag(parser.namespace, currentTag)

                        // Copy attributes
                        for (i in 0 until parser.attributeCount) {
                            serializer.attribute(
                                parser.getAttributeNamespace(i),
                                parser.getAttributeName(i),
                                parser.getAttributeValue(i)
                            )
                        }

                        if (currentTag == "game") {
                            inGameTag = true
                            gamePath = null
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text
                        if (!text.isNullOrBlank()) {
                            if (inGameTag && currentTag == "path") {
                                gamePath = text.trim()
                            }
                            serializer.text(text)
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name

                        if (tagName == "game" && inGameTag) {
                            inGameTag = false
                            gamePath?.let { path ->
                                val gameFileName = extractGameFileName(path)
                                copyMediaForGame(
                                    context,
                                    serializer,
                                    gameFileName,
                                    coversDir,
                                    fanartDir,
                                    screenshotsDir,
                                    imageDir,
                                    thumbnailDir
                                )
                                gamesProcessed++
                            }
                        }

                        serializer.endTag(parser.namespace, tagName)
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

            serializer.endDocument()
            Log.d(TAG, "Processed $gamesProcessed games")
            stringWriter.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing and enriching gamelist", e)
            null
        }
    }

    private fun extractGameFileName(path: String): String {
        val filename = path.substringAfterLast('/')
        // Remove any file extension (e.g., .zcci, .nds, .gba, etc.)
        val extracted = filename.substringBeforeLast('.')
        Log.d(TAG, "Extracted filename from path '$path': '$extracted'")
        return extracted
    }

    private fun writeGamelist(context: Context, toSystemDir: DocumentFile, xmlContent: String) {
        try {
            // Delete existing gamelist if it exists
            toSystemDir.findFile("gamelist.xml")?.let { existingFile ->
                if (!existingFile.delete()) {
                    Log.w(TAG, "Failed to delete existing gamelist.xml")
                }
            }

            // Create new gamelist file
            val gamelistFile = toSystemDir.createFile("application/xml", "gamelist.xml")
            if (gamelistFile == null) {
                Log.e(TAG, "Failed to create gamelist.xml in ${toSystemDir.name}")
                return
            }

            context.contentResolver.openOutputStream(gamelistFile.uri)?.use { outputStream ->
                outputStream.writer().use { writer ->
                    writer.write(xmlContent)
                    writer.flush()
                }
            }

            Log.d(TAG, "Successfully wrote gamelist.xml to ${toSystemDir.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing gamelist", e)
        }
    }

    private fun copyMediaForGame(
        context: Context,
        serializer: XmlSerializer,
        gameFileName: String,
        coversDir: DocumentFile?,
        fanartDir: DocumentFile?,
        screenshotsDir: DocumentFile?,
        imageDir: DocumentFile,
        thumbnailDir: DocumentFile
    ) {
        var mediaCopied = false

        Log.d(TAG, "Looking for media for: $gameFileName")

        // Copy cover to thumbnail
        val coverFile = findMediaFile(coversDir, gameFileName)
        if (coverFile != null) {
            Log.d(TAG, "Found cover: ${coverFile.name}")
            if (copyAndAddTag(context, coverFile, thumbnailDir, serializer, "thumbnail", "./media/thumbnail/")) {
                mediaCopied = true
            }
        } else {
            Log.d(TAG, "No cover found in ${coversDir?.name}")
        }

        // Copy fanart to image, fallback to screenshot
        val fanartFile = findMediaFile(fanartDir, gameFileName)
        val screenshotFile = if (fanartFile == null) findMediaFile(screenshotsDir, gameFileName) else null
        val imageSource = fanartFile ?: screenshotFile

        if (imageSource != null) {
            Log.d(TAG, "Found image: ${imageSource.name} (from ${if (fanartFile != null) "fanart" else "screenshots"})")
            if (copyAndAddTag(context, imageSource, imageDir, serializer, "image", "./media/image/")) {
                mediaCopied = true
            }
        } else {
            Log.d(TAG, "No fanart or screenshot found")
        }

        if (mediaCopied) {
            Log.d(TAG, "Copied media for game: $gameFileName")
        } else {
            Log.w(TAG, "No media copied for game: $gameFileName")
        }
    }

    private fun findMediaFile(directory: DocumentFile?, gameFileName: String): DocumentFile? {
        if (directory == null) {
            Log.d(TAG, "Directory is null")
            return null
        }

        return try {
            val files = directory.listFiles()
            Log.d(TAG, "Searching ${files.size} files in ${directory.name} for: $gameFileName")

            val found = files.find {
                val matches = it.name?.startsWith(gameFileName) == true
                if (matches) {
                    Log.d(TAG, "Match: ${it.name}")
                }
                matches
            }

            if (found == null) {
                Log.d(TAG, "No match found. First 3 files: ${files.take(3).mapNotNull { it.name }.joinToString()}")
            }

            found
        } catch (e: Exception) {
            Log.e(TAG, "Error finding media file for $gameFileName", e)
            null
        }
    }

    private fun copyAndAddTag(
        context: Context,
        sourceFile: DocumentFile,
        targetDir: DocumentFile,
        serializer: XmlSerializer,
        tagName: String,
        pathPrefix: String
    ): Boolean {
        return try {
            val fileName = sourceFile.name
            if (fileName == null) {
                Log.w(TAG, "Source file has no name")
                return false
            }

            // Check if file already exists and delete it
            targetDir.findFile(fileName)?.let { existingFile ->
                if (!existingFile.delete()) {
                    Log.w(TAG, "Failed to delete existing file: $fileName")
                }
            }

            val newFile = targetDir.createFile("image/*", fileName)
            if (newFile == null) {
                Log.e(TAG, "Failed to create file: $fileName in ${targetDir.name}")
                return false
            }

            var copySuccess = false
            context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    val bytesCopied = input.copyTo(output)
                    output.flush()
                    copySuccess = bytesCopied > 0
                }
            }

            if (!copySuccess) {
                Log.e(TAG, "Failed to copy file content: $fileName")
                newFile.delete()
                return false
            }

            serializer.startTag(null, tagName)
            serializer.text("$pathPrefix${newFile.name}")
            serializer.endTag(null, tagName)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying and adding tag for $tagName", e)
            false
        }
    }
}