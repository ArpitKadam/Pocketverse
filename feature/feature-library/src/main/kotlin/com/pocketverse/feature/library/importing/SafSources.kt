// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.feature.importing.api.GameSource
import com.pocketverse.feature.importing.api.ImportLimits
import com.pocketverse.feature.importing.api.SourceEntry
import java.io.IOException
import java.io.InputStream

/** The picked tree is bigger or deeper than the safety limits allow; thrown while listing, before anything is read. */
class SourceLimitException(message: String) : IOException(message)

/** One file or folder in a picked tree. [id] is the provider's own identifier. */
data class DocNode(val id: String, val name: String, val isDirectory: Boolean, val size: Long)

/** The few operations a tree source needs; the Android implementation is [DocumentsTree]. */
interface DocTree {
    val rootName: String

    /** Children of [parent], or of the picked root when null. */
    fun children(parent: DocNode?): List<DocNode>

    fun open(node: DocNode): InputStream
}

/** A folder picked with the Storage Access Framework, seen as a [GameSource]. Listing stops at the safety limits. */
class TreeSource(private val tree: DocTree, private val limits: ImportLimits = ImportLimits()) : GameSource {
    override val displayName: String get() = tree.rootName
    override val kind: SourceKind get() = SourceKind.FOLDER

    private val index: Map<String, Pair<SourceEntry, DocNode>> by lazy { list() }

    override fun entries(): List<SourceEntry> = index.values.map { it.first }

    override fun open(path: String): InputStream = tree.open((index[path] ?: throw IOException("'$path' is not in the picked folder")).second)

    private fun list(): Map<String, Pair<SourceEntry, DocNode>> {
        val files = LinkedHashMap<String, Pair<SourceEntry, DocNode>>()
        var seen = 0
        // Explicit stack: no recursion depth to attack. Every file and folder counts, and listing stops at the limit.
        val stack = ArrayDeque<Pair<String, DocNode?>>()
        stack.addLast("" to null)
        while (stack.isNotEmpty()) {
            val (prefix, parent) = stack.removeLast()
            for (child in tree.children(parent)) {
                if (child.name.isEmpty() || '/' in child.name || child.name == "." || child.name == "..") continue
                if (++seen > limits.maxEntries) throw SourceLimitException("more than ${limits.maxEntries} files and folders")
                val path = prefix + child.name
                if (child.isDirectory) {
                    if (path.count { it == '/' } + 1 > MAX_DEPTH) throw SourceLimitException("folders nested deeper than $MAX_DEPTH levels")
                    stack.addLast("$path/" to child)
                } else {
                    files[path] = SourceEntry(path, child.size.coerceAtLeast(0)) to child
                }
            }
        }
        return files
    }

    private companion object {
        const val MAX_DEPTH = 64
    }
}

/** A single file picked with the Storage Access Framework. */
class UriFileSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val displayName: String,
    private val size: Long,
) : GameSource {
    override fun entries() = listOf(SourceEntry(displayName, size))

    override fun open(path: String): InputStream = resolver.openInputStream(uri) ?: throw IOException("cannot open $displayName")
}

/** Name and size of a picked document, either of which may be unknown. */
data class PickedFile(val name: String, val size: Long)

/** Reads a picked file's name and size from its provider. */
fun describe(resolver: ContentResolver, uri: Uri): PickedFile {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "game"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    return PickedFile(name, size)
}

/** A tree picked with `ACTION_OPEN_DOCUMENT_TREE`, read through the provider. Not covered by unit tests: it needs a real provider. */
class DocumentsTree(private val resolver: ContentResolver, private val treeUri: Uri) : DocTree {
    private val rootId = DocumentsContract.getTreeDocumentId(treeUri)

    override val rootName: String by lazy {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "game"
    }

    override fun children(parent: DocNode?): List<DocNode> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent?.id ?: rootId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val out = mutableListOf<DocNode>()
        resolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += DocNode(
                    id = c.getString(0),
                    name = c.getString(1) ?: continue,
                    isDirectory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (c.isNull(3)) -1 else c.getLong(3),
                )
            }
        } ?: throw IOException("cannot list the picked folder")
        return out
    }

    override fun open(node: DocNode): InputStream =
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, node.id)) ?: throw IOException("cannot open ${node.name}")
}
