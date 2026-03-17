package com.tokama.linkmanager

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tokama.linkmanager.data.SavedFile
import com.tokama.linkmanager.storage.FileRepository
import com.tokama.linkmanager.storage.SavedFilesStore
import com.tokama.linkmanager.ui.FileListAdapter

class MainActivity : AppCompatActivity() {

    private var appliedUiStateSignature: String = ""

    private lateinit var statusBarSpacer: View
    private lateinit var toolbar: Toolbar
    private lateinit var createFileButton: ImageButton
    private lateinit var filesRecyclerView: RecyclerView
    private lateinit var infoText: TextView
    private lateinit var emptyText: TextView

    private lateinit var fileRepository: FileRepository
    private lateinit var savedFilesStore: SavedFilesStore
    private lateinit var adapter: FileListAdapter
    private lateinit var fileDragHelper: ItemTouchHelper

    private var pendingNewFileName: String = "links.txt"
    private var hasPendingFileOrderChange = false

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        handleSelectedFile(uri, result.data?.flags ?: 0)
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        handleCreatedFile(uri, result.data?.flags ?: 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUiSettings.applySavedNightMode(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = !AppUiSettings.isDarkModeActive(this)

        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        fileRepository = FileRepository(this)
        savedFilesStore = SavedFilesStore(this)

        statusBarSpacer = findViewById(R.id.statusBarSpacerMain)
        toolbar = findViewById(R.id.toolbarMain)
        createFileButton = findViewById(R.id.btnCreateFile)
        filesRecyclerView = findViewById(R.id.rvFiles)
        infoText = findViewById(R.id.tvInfo)
        emptyText = findViewById(R.id.tvEmptyFiles)

        applySystemBarInsets()
        setupToolbar()
        setupRecyclerView()
        createFileButton.setOnClickListener { showCreateFileDialog() }

        appliedUiStateSignature = AppUiSettings.buildUiStateSignature(this)
    }

    override fun onResume() {
        super.onResume()

        val currentUiStateSignature = AppUiSettings.buildUiStateSignature(this)
        if (currentUiStateSignature != appliedUiStateSignature) {
            recreate()
            return
        }

        loadFiles()
    }

    private fun applySystemBarInsets() {
        val recyclerBaseBottomMargin =
            (filesRecyclerView.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin
                ?: 0

        val createFileBaseBottomMargin =
            (createFileButton.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin
                ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpacer) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val layoutParams = view.layoutParams

            if (layoutParams.height != statusBarInsets.top) {
                layoutParams.height = statusBarInsets.top
                view.layoutParams = layoutParams
            }

            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(filesRecyclerView) { view, windowInsets ->
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = recyclerBaseBottomMargin + navigationBarInsets.bottom
            }
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(createFileButton) { view, windowInsets ->
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                bottomMargin = createFileBaseBottomMargin + navigationBarInsets.bottom
            }
            windowInsets
        }

        ViewCompat.requestApplyInsets(findViewById(android.R.id.content))
    }

    private fun setupToolbar() {
        toolbar.title = getString(R.string.app_name)
        toolbar.subtitle = null
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.main_menu)

        (toolbar.menu as? MenuBuilder)?.setOptionalIconsVisible(true)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }

                R.id.action_add_file -> {
                    openFilePicker()
                    true
                }

                R.id.action_share_app -> {
                    shareApp()
                    true
                }

                else -> false
            }
        }
    }

    private fun shareApp() {
        /*
            Der Share-Intent verwendet bewusst nur Plain-Text, damit möglichst viele
            Apps (Messenger, Mail, Notizen usw.) direkt als Ziel angeboten werden.
         */
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_message))
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app_chooser_title)))
    }

    private fun setupRecyclerView() {
        adapter = FileListAdapter(
            onClick = { savedFile -> openLinksScreen(savedFile) },
            onLongClick = { savedFile -> showFileOptions(savedFile) },
            onStartDrag = { viewHolder -> fileDragHelper.startDrag(viewHolder) }
        )

        filesRecyclerView.layoutManager = LinearLayoutManager(this)
        filesRecyclerView.adapter = adapter
        filesRecyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        fileDragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                hasPendingFileOrderChange = true
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (hasPendingFileOrderChange) {
                    hasPendingFileOrderChange = false
                    savedFilesStore.replaceAllInOrder(adapter.getCurrentItems())
                    loadFiles()
                }
            }
        })

        fileDragHelper.attachToRecyclerView(filesRecyclerView)
    }

    private fun loadFiles() {
        val files = savedFilesStore.getAll()
        adapter.submitList(files)

        val hasFiles = files.isNotEmpty()
        infoText.visibility = if (hasFiles) View.VISIBLE else View.GONE
        emptyText.visibility = if (hasFiles) View.GONE else View.VISIBLE
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/plain", "text/*", "application/octet-stream")
            )
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        openDocumentLauncher.launch(intent)
    }

    private fun showCreateFileDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.create_file_hint)
            setText("links")
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_file_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val rawName = input.text?.toString()?.trim().orEmpty()
                val finalName = buildTargetFileName(rawName)
                pendingNewFileName = finalName
                openCreateFilePicker(finalName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun buildTargetFileName(rawName: String): String {
        val cleaned = rawName.ifBlank { "links" }

        return when {
            cleaned.endsWith(".txt", ignoreCase = true) -> cleaned
            cleaned.endsWith(".lst", ignoreCase = true) -> cleaned.dropLast(4) + ".txt"
            else -> "$cleaned.txt"
        }
    }

    private fun openCreateFilePicker(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        createDocumentLauncher.launch(intent)
    }

    private fun handleSelectedFile(uri: Uri, resultFlags: Int) {
        val permissionFlags = resultFlags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        try {
            contentResolver.takePersistableUriPermission(uri, permissionFlags)
        } catch (_: Exception) {
        }

        val displayName = fileRepository.getDisplayName(uri)
        val added = savedFilesStore.add(
            SavedFile(
                uriString = uri.toString(),
                displayName = displayName
            )
        )

        Toast.makeText(
            this,
            if (added) getString(R.string.file_added) else getString(R.string.file_already_added),
            Toast.LENGTH_SHORT
        ).show()

        loadFiles()
    }

    private fun handleCreatedFile(uri: Uri, resultFlags: Int) {
        val permissionFlags = resultFlags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        try {
            contentResolver.takePersistableUriPermission(uri, permissionFlags)
        } catch (_: Exception) {
        }

        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write("")
        }

        val displayName = fileRepository.getDisplayName(uri).ifBlank { pendingNewFileName }

        val added = savedFilesStore.add(
            SavedFile(
                uriString = uri.toString(),
                displayName = displayName
            )
        )

        Toast.makeText(
            this,
            if (added) getString(R.string.file_created) else getString(R.string.file_already_added),
            Toast.LENGTH_SHORT
        ).show()

        loadFiles()
    }

    private fun openLinksScreen(savedFile: SavedFile) {
        val intent = Intent(this, LinksActivity::class.java).apply {
            putExtra(LinksActivity.EXTRA_FILE_URI, savedFile.uriString)
            putExtra(LinksActivity.EXTRA_FILE_NAME, savedFile.displayName)
        }
        startActivity(intent)
    }

    private fun showFileOptions(savedFile: SavedFile) {
        val options = arrayOf(
            getString(R.string.rename_file),
            getString(R.string.remove_from_list_only),
            getString(R.string.delete_file_really)
        )

        AlertDialog.Builder(this)
            .setTitle(stripKnownExtension(savedFile.displayName))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameFileDialog(savedFile)
                    1 -> confirmRemoveFromList(savedFile)
                    2 -> confirmDeleteFileReally(savedFile)
                }
            }
            .show()
    }

    private fun showRenameFileDialog(savedFile: SavedFile) {
        val originalDisplayName = savedFile.displayName
        val originalExtension = extractKnownExtension(originalDisplayName)
        val visibleBaseName = stripKnownExtension(originalDisplayName)

        val input = EditText(this).apply {
            hint = getString(R.string.rename_file_hint)
            setText(visibleBaseName)
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.rename_file_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val enteredBaseName = input.text?.toString()?.trim().orEmpty()
                if (enteredBaseName.isNotBlank()) {
                    val finalFileName = enteredBaseName + originalExtension
                    renameFile(savedFile, finalFileName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun stripKnownExtension(fileName: String): String {
        return when {
            fileName.endsWith(".lst", ignoreCase = true) -> fileName.dropLast(4)
            fileName.endsWith(".txt", ignoreCase = true) -> fileName.dropLast(4)
            else -> fileName
        }
    }

    private fun extractKnownExtension(fileName: String): String {
        return when {
            fileName.endsWith(".lst", ignoreCase = true) -> ".lst"
            fileName.endsWith(".txt", ignoreCase = true) -> ".txt"
            else -> ""
        }
    }

    private fun renameFile(savedFile: SavedFile, newName: String) {
        try {
            val oldUri = Uri.parse(savedFile.uriString)
            val renamedUri = DocumentsContract.renameDocument(contentResolver, oldUri, newName)

            if (renamedUri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        renamedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                val newDisplayName = fileRepository.getDisplayName(renamedUri).ifBlank { newName }

                savedFilesStore.update(
                    oldUriString = savedFile.uriString,
                    newFile = savedFile.copy(
                        uriString = renamedUri.toString(),
                        displayName = newDisplayName
                    )
                )

                loadFiles()
                Toast.makeText(this, R.string.file_renamed_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.file_renamed_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.file_renamed_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmRemoveFromList(savedFile: SavedFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_file_title)
            .setMessage(getString(R.string.remove_file_message, savedFile.displayName))
            .setPositiveButton(R.string.remove) { _, _ ->
                savedFilesStore.remove(savedFile.uriString)
                loadFiles()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteFileReally(savedFile: SavedFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_file_really_title)
            .setMessage(getString(R.string.delete_file_really_message, savedFile.displayName))
            .setPositiveButton(R.string.delete_file_really) { _, _ ->
                deleteFileReally(savedFile)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteFileReally(savedFile: SavedFile) {
        try {
            val uri = Uri.parse(savedFile.uriString)
            val deleted = DocumentsContract.deleteDocument(contentResolver, uri)

            if (deleted) {
                savedFilesStore.remove(savedFile.uriString)
                loadFiles()
                Toast.makeText(this, R.string.file_deleted_really_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.file_deleted_really_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.file_deleted_really_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
