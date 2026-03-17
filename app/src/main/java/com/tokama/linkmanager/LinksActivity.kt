package com.tokama.linkmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tokama.linkmanager.data.LinkEntry
import com.tokama.linkmanager.data.LinkRating
import com.tokama.linkmanager.storage.FileRepository
import com.tokama.linkmanager.ui.LinkAdapter
import com.tokama.linkmanager.util.BrowserManager
import com.tokama.linkmanager.util.LinkParser
import com.tokama.linkmanager.util.OpenedLinksSession

class LinksActivity : AppCompatActivity() {

    private lateinit var fileUri: Uri
    private lateinit var fileName: String
    private lateinit var statusBarSpacer: View
    private lateinit var toolbar: Toolbar
    private lateinit var browserInfoText: TextView
    private lateinit var addLinkButton: Button
    private lateinit var chooseBrowserButton: Button
    private lateinit var linksRecyclerView: RecyclerView
    private lateinit var emptyText: TextView

    private lateinit var fileRepository: FileRepository
    private lateinit var adapter: LinkAdapter
    private lateinit var filterPreferences: SharedPreferences
    private lateinit var linkDragHelper: ItemTouchHelper

    private var currentEntries: List<LinkEntry> = emptyList()
    private var displayedEntriesCount = 0
    private var totalEntriesCount = 0
    private var scrollToBottomOnFirstLoad = true
    private var scrollToBottomOnNextLoad = false
    private var filterState = LinkFilterState()
    private var filterMenuItem: MenuItem? = null
    private val selectedEntryKeys = mutableSetOf<String>()
    private var isSelectionMode = false
    private var hasPendingLinkOrderChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUiSettings.applySavedNightMode(this)
        super.onCreate(savedInstanceState)

        /*
            Auf aktuellen Android-Versionen wird Edge-to-Edge aktiv.
            Deshalb übernehmen wir die System-Insets hier selbst:
            - der Statusleistenbereich wird über einen separaten grünen Spacer dargestellt
            - die Link-Liste bekommt unten zusätzlich den Navigation-Bar-Inset
         */
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = !AppUiSettings.isDarkModeActive(this)

        setContentView(R.layout.activity_links)
        supportActionBar?.hide()

        fileRepository = FileRepository(this)
        filterPreferences = getSharedPreferences(PREFS_LINK_FILTER, MODE_PRIVATE)

        val uriString = intent.getStringExtra(EXTRA_FILE_URI).orEmpty()
        fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifBlank {
            getString(R.string.unknown_file)
        }
        fileUri = Uri.parse(uriString)

        statusBarSpacer = findViewById(R.id.statusBarSpacerLinks)
        toolbar = findViewById(R.id.toolbarLinks)
        browserInfoText = findViewById(R.id.tvBrowserInfo)
        addLinkButton = findViewById(R.id.btnAddLink)
        chooseBrowserButton = findViewById(R.id.btnChooseBrowser)
        linksRecyclerView = findViewById(R.id.rvLinks)
        emptyText = findViewById(R.id.tvEmptyLinks)

        applySystemBarInsets()

        filterState = if (savedInstanceState != null) {
            restoreFilterState(savedInstanceState)
        } else {
            loadPersistedFilterState()
        }

        setupToolbar()
        setupRecyclerView()
        setupActions()
        setupBackHandling()
        updateHeaderState()
    }

    override fun onResume() {
        super.onResume()
        loadLinks()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            STATE_ALLOWED_RATINGS,
            ArrayList(filterState.allowedRatings.map { it.name })
        )
        outState.putString(STATE_SEEN_FILTER, filterState.seenFilter.name)
        outState.putString(STATE_SORT_BY, filterState.sortBy.name)
        outState.putString(STATE_SORT_ORDER, filterState.sortOrder.name)
    }

    private fun applySystemBarInsets() {
        val recyclerBaseBottomMargin =
            (linksRecyclerView.layoutParams as? RecyclerView.LayoutParams)?.bottomMargin
                ?: (linksRecyclerView.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin
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

        ViewCompat.setOnApplyWindowInsetsListener(linksRecyclerView) { view, windowInsets ->
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = recyclerBaseBottomMargin + navigationBarInsets.bottom
            }
            windowInsets
        }

        ViewCompat.requestApplyInsets(findViewById(android.R.id.content))
    }

    private fun setupToolbar() {
        toolbar.title = stripKnownExtension(fileName)
        toolbar.subtitle = null

        val baseNavigationIcon = ContextCompat.getDrawable(
            this,
            androidx.appcompat.R.drawable.abc_ic_ab_back_material
        )?.mutate()

        if (baseNavigationIcon != null) {
            val wrappedNavigationIcon = DrawableCompat.wrap(baseNavigationIcon)
            DrawableCompat.setTint(
                wrappedNavigationIcon,
                ContextCompat.getColor(this, android.R.color.white)
            )
            toolbar.navigationIcon = wrappedNavigationIcon
        } else {
            toolbar.navigationIcon = null
        }

        toolbar.navigationContentDescription =
            getString(androidx.appcompat.R.string.abc_action_bar_up_description)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.menu_links)
        filterMenuItem = toolbar.menu.findItem(R.id.action_filter_links)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_filter_links -> {
                    showFilterDialog()
                    true
                }
                else -> false
            }
        }

        updateFilterMenuState()
    }

    private fun setupRecyclerView() {
        adapter = LinkAdapter(
            onClick = { entry ->
                if (isSelectionMode) {
                    toggleSelection(entry)
                } else {
                    OpenedLinksSession.markOpened(entry.url)
                    loadLinks()
                    BrowserManager.openLink(this, entry.url)
                }
            },
            onLongClick = { entry ->
                if (isSelectionMode) {
                    toggleSelection(entry)
                } else {
                    enterSelectionMode(entry)
                }
            },
            onBadgeClick = { entry ->
                if (isSelectionMode) {
                    toggleSelection(entry)
                } else {
                    showLinkOptions(entry)
                }
            },
            onStartDrag = { viewHolder ->
                if (canStartLinkDrag()) {
                    linkDragHelper.startDrag(viewHolder)
                } else {
                    Toast.makeText(
                        this,
                        R.string.reorder_requires_original_order,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        linksRecyclerView.layoutManager = LinearLayoutManager(this)
        linksRecyclerView.adapter = adapter
        linksRecyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        linkDragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (!canStartLinkDrag()) return false
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                hasPendingLinkOrderChange = true
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (canStartLinkDrag() && hasPendingLinkOrderChange) {
                    hasPendingLinkOrderChange = false
                    persistDraggedLinkOrder()
                }
            }
        })

        linkDragHelper.attachToRecyclerView(linksRecyclerView)
    }

    private fun setupActions() {
        chooseBrowserButton.setOnClickListener {
            if (isSelectionMode) {
                confirmDeleteSelected()
            } else {
                showBrowserSelectionDialog()
            }
        }

        addLinkButton.setOnClickListener {
            if (isSelectionMode) {
                openSelectedLinks()
            } else {
                showAddLinkDialog()
            }
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    finish()
                }
            }
        })
    }

    private fun styleActionButtons(primaryColorRes: Int, secondaryColorRes: Int) {
        chooseBrowserButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, primaryColorRes)
        )
        chooseBrowserButton.setTextColor(
            ContextCompat.getColor(this, R.color.actionTextOnColor)
        )

        addLinkButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, secondaryColorRes)
        )
        addLinkButton.setTextColor(
            ContextCompat.getColor(this, R.color.actionTextOnColor)
        )
    }

    private fun updateHeaderState() {
        if (isSelectionMode) {
            toolbar.title = getString(R.string.selected_count_title, selectedEntryKeys.size)
            toolbar.subtitle = null
            browserInfoText.text = getString(R.string.selection_mode_hint)
            chooseBrowserButton.text = getString(R.string.delete)
            addLinkButton.text = getString(R.string.action_open_selected)
            styleActionButtons(
                primaryColorRes = R.color.actionDanger,
                secondaryColorRes = R.color.actionPrimary
            )
        } else {
            toolbar.title = stripKnownExtension(fileName)
            toolbar.subtitle = buildToolbarSubtitle()
            browserInfoText.text = getString(
                R.string.selected_browser,
                BrowserManager.getSelectedBrowserLabel(this)
            )
            chooseBrowserButton.text = getString(R.string.choose_browser)
            addLinkButton.text = getString(R.string.add_link)
            styleActionButtons(
                primaryColorRes = R.color.actionNeutral,
                secondaryColorRes = R.color.actionPrimary
            )
        }

        updateFilterMenuState()
    }

    private fun loadLinks() {
        currentEntries = try {
            fileRepository.readLinks(fileUri)
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.read_error),
                Toast.LENGTH_SHORT
            ).show()
            emptyList()
        }

        val validKeys = currentEntries.map { selectionKey(it) }.toSet()
        selectedEntryKeys.retainAll(validKeys)

        if (isSelectionMode && selectedEntryKeys.isEmpty()) {
            isSelectionMode = false
        }

        val displayedEntries = applyFilters(currentEntries)
        displayedEntriesCount = displayedEntries.size
        totalEntriesCount = currentEntries.size
        adapter.submitList(displayedEntries)
        adapter.submitSelection(selectedEntryKeys)
        adapter.submitOpenedUrls(OpenedLinksSession.getAll())

        emptyText.visibility = if (displayedEntries.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (currentEntries.isEmpty()) {
            getString(R.string.no_links)
        } else {
            getString(R.string.no_links_for_filter)
        }

        updateHeaderState()

        if ((scrollToBottomOnFirstLoad || scrollToBottomOnNextLoad) && displayedEntries.isNotEmpty()) {
            linksRecyclerView.post {
                linksRecyclerView.scrollToPosition(displayedEntries.lastIndex)
            }
        }

        scrollToBottomOnFirstLoad = false
        scrollToBottomOnNextLoad = false
    }

    private fun showAddLinkDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.add_link_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_link)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                addLinkFromInput(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addLinkFromInput(rawInput: String) {
        val url = LinkParser.extractFirstUrl(rawInput)
        if (url == null) {
            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
            return
        }

        if (currentEntries.any { it.url == url }) {
            Toast.makeText(this, R.string.duplicate_link, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            fileRepository.appendLink(fileUri, url)
            loadLinks()
            Toast.makeText(this, R.string.link_added, Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.write_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showBrowserSelectionDialog() {
        val options = BrowserManager.getSupportedBrowsers(this)
            .filter { it.mode != BrowserManager.BrowserMode.PRIVATE_EXPERIMENTAL }

        if (options.isEmpty()) {
            Toast.makeText(this, R.string.no_supported_browser, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = options.map { it.label }.toTypedArray()
        val selectedId = BrowserManager.getSelectedBrowserId(this)
        var selectedIndex = options.indexOfFirst { it.id == selectedId }
        if (selectedIndex < 0) selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_browser)
            .setSingleChoiceItems(labels, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.save) { _, _ ->
                BrowserManager.saveSelectedBrowser(this, options[selectedIndex].id)
                updateHeaderState()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFilterDialog() {
        if (isSelectionMode) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_link_filter, null)

        val seenGroup = dialogView.findViewById<RadioGroup>(R.id.rgSeenFilter)
        val ratingNone = dialogView.findViewById<CheckBox>(R.id.cbRatingNone)
        val ratingVeryGood = dialogView.findViewById<CheckBox>(R.id.cbRatingVeryGood)
        val ratingGood = dialogView.findViewById<CheckBox>(R.id.cbRatingGood)
        val ratingOkay = dialogView.findViewById<CheckBox>(R.id.cbRatingOkay)
        val ratingBad = dialogView.findViewById<CheckBox>(R.id.cbRatingBad)
        val ratingVeryBad = dialogView.findViewById<CheckBox>(R.id.cbRatingVeryBad)
        val sortByGroup = dialogView.findViewById<RadioGroup>(R.id.rgSortBy)
        val sortOrderGroup = dialogView.findViewById<RadioGroup>(R.id.rgSortOrder)
        val resetButton = dialogView.findViewById<Button>(R.id.btnFilterReset)
        val cancelButton = dialogView.findViewById<Button>(R.id.btnFilterCancel)
        val applyButton = dialogView.findViewById<Button>(R.id.btnFilterApply)

        when (filterState.seenFilter) {
            SeenFilter.ALL -> seenGroup.check(R.id.rbSeenAll)
            SeenFilter.OPENED -> seenGroup.check(R.id.rbSeenOnlyOpened)
            SeenFilter.UNOPENED -> seenGroup.check(R.id.rbSeenOnlyUnopened)
        }

        ratingNone.isChecked = filterState.allowedRatings.contains(LinkRating.NONE)
        ratingVeryGood.isChecked = filterState.allowedRatings.contains(LinkRating.VERY_GOOD)
        ratingGood.isChecked = filterState.allowedRatings.contains(LinkRating.GOOD)
        ratingOkay.isChecked = filterState.allowedRatings.contains(LinkRating.OKAY)
        ratingBad.isChecked = filterState.allowedRatings.contains(LinkRating.BAD)
        ratingVeryBad.isChecked = filterState.allowedRatings.contains(LinkRating.VERY_BAD)

        when (filterState.sortBy) {
            SortBy.ORIGINAL -> sortByGroup.check(R.id.rbSortByOriginal)
            SortBy.RATING -> sortByGroup.check(R.id.rbSortByRating)
            SortBy.URL -> sortByGroup.check(R.id.rbSortByAlphabetical)
        }

        when (filterState.sortOrder) {
            SortOrder.ASC -> sortOrderGroup.check(R.id.rbSortOrderAsc)
            SortOrder.DESC -> sortOrderGroup.check(R.id.rbSortOrderDesc)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.filter_title)
            .setView(dialogView)
            .create()

        resetButton.setOnClickListener {
            applyNewFilterState(LinkFilterState())
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        applyButton.setOnClickListener {
            val allowedRatings = linkedSetOf<LinkRating>().apply {
                if (ratingNone.isChecked) add(LinkRating.NONE)
                if (ratingVeryGood.isChecked) add(LinkRating.VERY_GOOD)
                if (ratingGood.isChecked) add(LinkRating.GOOD)
                if (ratingOkay.isChecked) add(LinkRating.OKAY)
                if (ratingBad.isChecked) add(LinkRating.BAD)
                if (ratingVeryBad.isChecked) add(LinkRating.VERY_BAD)
            }

            if (allowedRatings.isEmpty()) {
                Toast.makeText(
                    this,
                    R.string.filter_select_at_least_one_rating,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val seenFilter = when (seenGroup.checkedRadioButtonId) {
                R.id.rbSeenOnlyOpened -> SeenFilter.OPENED
                R.id.rbSeenOnlyUnopened -> SeenFilter.UNOPENED
                else -> SeenFilter.ALL
            }

            val sortBy = when (sortByGroup.checkedRadioButtonId) {
                R.id.rbSortByRating -> SortBy.RATING
                R.id.rbSortByAlphabetical -> SortBy.URL
                else -> SortBy.ORIGINAL
            }

            val sortOrder = when (sortOrderGroup.checkedRadioButtonId) {
                R.id.rbSortOrderDesc -> SortOrder.DESC
                else -> SortOrder.ASC
            }

            applyNewFilterState(
                LinkFilterState(
                    allowedRatings = allowedRatings,
                    seenFilter = seenFilter,
                    sortBy = sortBy,
                    sortOrder = sortOrder
                )
            )

            dialog.dismiss()
        }

        dialog.show()

        val displayMetrics = resources.displayMetrics
        val targetWidth = (displayMetrics.widthPixels * 0.96f).toInt()
        val targetHeight = (displayMetrics.heightPixels * 0.86f).toInt()
        dialog.window?.setLayout(targetWidth, targetHeight)
    }

    private fun showLinkOptions(entry: LinkEntry) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_link_actions, null)

        val tvDialogUrl = dialogView.findViewById<TextView>(R.id.tvDialogUrl)
        val actionClearOpened = dialogView.findViewById<TextView>(R.id.actionClearOpened)
        val dividerOpened = dialogView.findViewById<View>(R.id.dividerOpened)
        val actionRemoveRating = dialogView.findViewById<TextView>(R.id.actionRemoveRating)
        val dividerRemove = dialogView.findViewById<View>(R.id.dividerRemove)
        val actionVeryGood = dialogView.findViewById<TextView>(R.id.actionVeryGood)
        val actionGood = dialogView.findViewById<TextView>(R.id.actionGood)
        val actionOkay = dialogView.findViewById<TextView>(R.id.actionOkay)
        val actionBad = dialogView.findViewById<TextView>(R.id.actionBad)
        val actionVeryBad = dialogView.findViewById<TextView>(R.id.actionVeryBad)
        val actionDelete = dialogView.findViewById<TextView>(R.id.actionDelete)
        val actionCancel = dialogView.findViewById<TextView>(R.id.actionCancel)

        tvDialogUrl.text = entry.url

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        tvDialogUrl.setOnLongClickListener {
            copyUrlToClipboard(entry.url)
            true
        }

        val wasOpened = OpenedLinksSession.isOpened(entry.url)
        if (!wasOpened) {
            actionClearOpened.visibility = View.GONE
            dividerOpened.visibility = View.GONE
        }

        if (entry.rating == LinkRating.NONE) {
            actionRemoveRating.visibility = View.GONE
            dividerRemove.visibility = View.GONE
        }

        bindDialogAction(actionClearOpened, dialog) {
            OpenedLinksSession.unmarkOpened(entry.url)
            loadLinks()
        }
        bindDialogAction(actionRemoveRating, dialog) {
            updateRating(entry, LinkRating.NONE)
        }
        bindDialogAction(actionVeryGood, dialog) {
            updateRating(entry, LinkRating.VERY_GOOD)
        }
        bindDialogAction(actionGood, dialog) {
            updateRating(entry, LinkRating.GOOD)
        }
        bindDialogAction(actionOkay, dialog) {
            updateRating(entry, LinkRating.OKAY)
        }
        bindDialogAction(actionBad, dialog) {
            updateRating(entry, LinkRating.BAD)
        }
        bindDialogAction(actionVeryBad, dialog) {
            updateRating(entry, LinkRating.VERY_BAD)
        }
        bindDialogAction(actionDelete, dialog) {
            confirmDelete(entry)
        }

        actionCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun bindDialogAction(view: View, dialog: AlertDialog, action: () -> Unit) {
        view.setOnClickListener {
            dialog.dismiss()
            action()
        }
    }

    private fun copyUrlToClipboard(url: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clipData = ClipData.newPlainText(getString(R.string.link_list_title), url)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(entry: LinkEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_link_title)
            .setMessage(getString(R.string.delete_link_message, entry.url))
            .setPositiveButton(R.string.delete) { _, _ ->
                try {
                    fileRepository.deleteLine(fileUri, entry)
                    loadLinks()
                } catch (exception: Exception) {
                    Toast.makeText(
                        this,
                        exception.message ?: getString(R.string.write_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateRating(entry: LinkEntry, rating: LinkRating) {
        try {
            fileRepository.updateRating(fileUri, entry, rating)
            loadLinks()
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.write_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun enterSelectionMode(entry: LinkEntry) {
        isSelectionMode = true
        toggleSelection(entry)
    }

    private fun toggleSelection(entry: LinkEntry) {
        val key = selectionKey(entry)

        if (selectedEntryKeys.contains(key)) {
            selectedEntryKeys.remove(key)
        } else {
            selectedEntryKeys.add(key)
        }

        if (selectedEntryKeys.isEmpty()) {
            exitSelectionMode()
            return
        }

        adapter.submitSelection(selectedEntryKeys)
        updateHeaderState()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedEntryKeys.clear()
        adapter.submitSelection(emptySet())
        updateHeaderState()
    }

    private fun openSelectedLinks() {
        val selectedEntries = getSelectedEntries()
        if (selectedEntries.isEmpty()) {
            Toast.makeText(this, R.string.no_links_selected, Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            return
        }

        selectedEntries.forEach { entry ->
            OpenedLinksSession.markOpened(entry.url)
        }

        exitSelectionMode()
        loadLinks()

        selectedEntries.forEach { entry ->
            BrowserManager.openLink(this, entry.url)
        }
    }

    private fun confirmDeleteSelected() {
        val selectedEntries = getSelectedEntries()
        if (selectedEntries.isEmpty()) {
            Toast.makeText(this, R.string.no_links_selected, Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_selected_title)
            .setMessage(getString(R.string.delete_selected_message, selectedEntries.size))
            .setPositiveButton(R.string.delete_selected_confirm) { _, _ ->
                try {
                    selectedEntries
                        .sortedByDescending { it.lineIndex }
                        .forEach { entry ->
                            fileRepository.deleteLine(fileUri, entry)
                        }

                    exitSelectionMode()
                    loadLinks()
                } catch (exception: Exception) {
                    Toast.makeText(
                        this,
                        exception.message ?: getString(R.string.write_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getSelectedEntries(): List<LinkEntry> {
        return currentEntries.filter { selectedEntryKeys.contains(selectionKey(it)) }
    }

    private fun applyFilters(entries: List<LinkEntry>): List<LinkEntry> {
        val filteredEntries = entries.filter { entry ->
            entry.rating in filterState.allowedRatings && matchesSeenFilter(entry)
        }

        return when (filterState.sortBy) {
            SortBy.ORIGINAL -> {
                if (filterState.sortOrder == SortOrder.ASC) {
                    filteredEntries.sortedBy { it.lineIndex }
                } else {
                    filteredEntries.sortedByDescending { it.lineIndex }
                }
            }

            SortBy.URL -> {
                val comparator = compareBy<LinkEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.url }
                    .thenBy { it.lineIndex }

                if (filterState.sortOrder == SortOrder.ASC) {
                    filteredEntries.sortedWith(comparator)
                } else {
                    filteredEntries.sortedWith(comparator.reversed())
                }
            }

            SortBy.RATING -> {
                val comparator = compareBy<LinkEntry> { ratingSortValue(it.rating) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.url }
                    .thenBy { it.lineIndex }

                if (filterState.sortOrder == SortOrder.ASC) {
                    filteredEntries.sortedWith(comparator)
                } else {
                    filteredEntries.sortedWith(comparator.reversed())
                }
            }
        }
    }

    private fun matchesSeenFilter(entry: LinkEntry): Boolean {
        val isOpened = OpenedLinksSession.isOpened(entry.url)

        return when (filterState.seenFilter) {
            SeenFilter.ALL -> true
            SeenFilter.OPENED -> isOpened
            SeenFilter.UNOPENED -> !isOpened
        }
    }

    private fun ratingSortValue(rating: LinkRating): Int {
        return when (rating) {
            LinkRating.NONE -> 0
            LinkRating.VERY_BAD -> 1
            LinkRating.BAD -> 2
            LinkRating.OKAY -> 3
            LinkRating.GOOD -> 4
            LinkRating.VERY_GOOD -> 5
        }
    }

    private fun buildBrowserInfoText(): String {
        val browserInfo = getString(
            R.string.selected_browser,
            BrowserManager.getSelectedBrowserLabel(this)
        )

        if (filterState.isDefault()) {
            return browserInfo
        }

        return browserInfo + "\n" + getString(
            R.string.filter_active_summary,
            buildFilterSummary()
        )
    }

    private fun buildFilterSummary(): String {
        val parts = mutableListOf<String>()

        if (filterState.seenFilter != SeenFilter.ALL) {
            parts.add(
                getString(
                    R.string.filter_summary_seen,
                    getSeenFilterLabel(filterState.seenFilter)
                )
            )
        }

        if (filterState.allowedRatings != ALL_RATINGS) {
            parts.add(
                getString(
                    R.string.filter_summary_ratings,
                    buildRatingsSummary(filterState.allowedRatings)
                )
            )
        }

        if (filterState.sortBy != SortBy.ORIGINAL || filterState.sortOrder != SortOrder.ASC) {
            parts.add(
                getString(
                    R.string.filter_summary_sort,
                    getSortByLabel(filterState.sortBy),
                    getSortOrderLabel(filterState.sortOrder)
                )
            )
        }

        return parts.joinToString(separator = " • ")
            .ifBlank { getString(R.string.filter_none) }
    }

    private fun buildRatingsSummary(ratings: Set<LinkRating>): String {
        return RATING_SUMMARY_ORDER
            .filter { ratings.contains(it) }
            .joinToString(separator = ", ") { getRatingLabel(it) }
    }

    private fun getSeenFilterLabel(seenFilter: SeenFilter): String {
        return when (seenFilter) {
            SeenFilter.ALL -> getString(R.string.filter_seen_all)
            SeenFilter.OPENED -> getString(R.string.filter_seen_only_opened)
            SeenFilter.UNOPENED -> getString(R.string.filter_seen_only_unopened)
        }
    }

    private fun getSortByLabel(sortBy: SortBy): String {
        return when (sortBy) {
            SortBy.ORIGINAL -> getString(R.string.filter_sort_original)
            SortBy.RATING -> getString(R.string.filter_sort_rating)
            SortBy.URL -> getString(R.string.filter_sort_url)
        }
    }

    private fun getSortOrderLabel(sortOrder: SortOrder): String {
        return when (sortOrder) {
            SortOrder.ASC -> getString(R.string.filter_sort_order_asc)
            SortOrder.DESC -> getString(R.string.filter_sort_order_desc)
        }
    }

    private fun getRatingLabel(rating: LinkRating): String {
        return when (rating) {
            LinkRating.NONE -> getString(R.string.rating_none)
            LinkRating.VERY_GOOD -> getString(R.string.rating_very_good)
            LinkRating.GOOD -> getString(R.string.rating_good)
            LinkRating.OKAY -> getString(R.string.rating_okay)
            LinkRating.BAD -> getString(R.string.rating_bad)
            LinkRating.VERY_BAD -> getString(R.string.rating_very_bad)
        }
    }

    private fun applyNewFilterState(newState: LinkFilterState) {
        filterState = newState
        persistFilterState()
        scrollToBottomOnNextLoad = true
        loadLinks()
    }

    private fun buildToolbarSubtitle(): String {
        return if (filterState.isDefault() || displayedEntriesCount == totalEntriesCount) {
            getString(R.string.links_count_subtitle, displayedEntriesCount)
        } else {
            getString(R.string.links_filtered_count_subtitle, displayedEntriesCount, totalEntriesCount)
        }
    }

    private fun persistFilterState() {
        filterPreferences.edit()
            .putStringSet(
                PREF_ALLOWED_RATINGS,
                filterState.allowedRatings.map { it.name }.toSet()
            )
            .putString(PREF_SEEN_FILTER, filterState.seenFilter.name)
            .putString(PREF_SORT_BY, filterState.sortBy.name)
            .putString(PREF_SORT_ORDER, filterState.sortOrder.name)
            .apply()
    }

    private fun loadPersistedFilterState(): LinkFilterState {
        val allowedRatings = filterPreferences.getStringSet(PREF_ALLOWED_RATINGS, null)
            ?.mapNotNull { ratingName ->
                runCatching { LinkRating.valueOf(ratingName) }.getOrNull()
            }
            ?.toSet()
            ?.ifEmpty { ALL_RATINGS }
            ?: ALL_RATINGS

        val seenFilter = filterPreferences.getString(PREF_SEEN_FILTER, null)
            ?.let { enumName -> runCatching { SeenFilter.valueOf(enumName) }.getOrNull() }
            ?: SeenFilter.ALL

        val sortBy = filterPreferences.getString(PREF_SORT_BY, null)
            ?.let { enumName -> runCatching { SortBy.valueOf(enumName) }.getOrNull() }
            ?: SortBy.ORIGINAL

        val sortOrder = filterPreferences.getString(PREF_SORT_ORDER, null)
            ?.let { enumName -> runCatching { SortOrder.valueOf(enumName) }.getOrNull() }
            ?: SortOrder.ASC

        return LinkFilterState(
            allowedRatings = allowedRatings,
            seenFilter = seenFilter,
            sortBy = sortBy,
            sortOrder = sortOrder
        )
    }

    private fun updateFilterMenuState() {
        filterMenuItem?.isVisible = !isSelectionMode
        filterMenuItem?.icon = buildFilterIcon(filterState.isDefault())
    }

    private fun buildFilterIcon(isDefault: Boolean): Drawable? {
        val iconRes = if (isDefault) {
            R.drawable.ic_filter_inactive
        } else {
            R.drawable.ic_filter_active
        }

        val baseIcon = ContextCompat.getDrawable(this, iconRes)?.mutate() ?: return null
        val wrappedIcon = DrawableCompat.wrap(baseIcon)
        DrawableCompat.setTint(wrappedIcon, ContextCompat.getColor(this, android.R.color.white))
        return wrappedIcon
    }

    private fun canStartLinkDrag(): Boolean {
        return !isSelectionMode &&
                filterState.sortBy == SortBy.ORIGINAL &&
                filterState.sortOrder == SortOrder.ASC
    }

    /**
     * Persistiert die neue Reihenfolge direkt in der Datei.
     *
     * Falls Status-/Bewertungsfilter aktiv sind, wird nur die sichtbare Teilmenge
     * innerhalb der Originalreihenfolge neu einsortiert. Nicht sichtbare Einträge
     * bleiben an ihrer relativen Position erhalten.
     */
    private fun persistDraggedLinkOrder() {
        val reorderedVisibleEntries = adapter.getCurrentItems()
        if (reorderedVisibleEntries.isEmpty() || currentEntries.isEmpty()) {
            return
        }

        val baseEntries = currentEntries.sortedBy { it.lineIndex }
        val visibleKeySet = reorderedVisibleEntries.map { selectionKey(it) }.toSet()
        val reorderedIterator = reorderedVisibleEntries.iterator()

        val reorderedAllEntries = baseEntries.map { entry ->
            if (visibleKeySet.contains(selectionKey(entry)) && reorderedIterator.hasNext()) {
                reorderedIterator.next()
            } else {
                entry
            }
        }

        try {
            fileRepository.reorderLinks(fileUri, reorderedAllEntries)
            loadLinks()
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.write_error),
                Toast.LENGTH_SHORT
            ).show()
            loadLinks()
        }
    }

    private fun restoreFilterState(savedInstanceState: Bundle): LinkFilterState {
        val allowedRatings = savedInstanceState
            .getStringArrayList(STATE_ALLOWED_RATINGS)
            ?.mapNotNull { ratingName ->
                runCatching { LinkRating.valueOf(ratingName) }.getOrNull()
            }
            ?.toSet()
            ?.ifEmpty { ALL_RATINGS }
            ?: ALL_RATINGS

        val seenFilter = savedInstanceState.getString(STATE_SEEN_FILTER)
            ?.let { enumName -> runCatching { SeenFilter.valueOf(enumName) }.getOrNull() }
            ?: SeenFilter.ALL

        val sortBy = savedInstanceState.getString(STATE_SORT_BY)
            ?.let { enumName -> runCatching { SortBy.valueOf(enumName) }.getOrNull() }
            ?: SortBy.ORIGINAL

        val sortOrder = savedInstanceState.getString(STATE_SORT_ORDER)
            ?.let { enumName -> runCatching { SortOrder.valueOf(enumName) }.getOrNull() }
            ?: SortOrder.ASC

        return LinkFilterState(
            allowedRatings = allowedRatings,
            seenFilter = seenFilter,
            sortBy = sortBy,
            sortOrder = sortOrder
        )
    }

    private fun selectionKey(entry: LinkEntry): String {
        return "${entry.lineIndex}::${entry.url}"
    }

    private fun stripKnownExtension(fileName: String): String {
        return when {
            fileName.endsWith(".lst", ignoreCase = true) -> fileName.dropLast(4)
            fileName.endsWith(".txt", ignoreCase = true) -> fileName.dropLast(4)
            else -> fileName
        }
    }

    private data class LinkFilterState(
        val allowedRatings: Set<LinkRating> = ALL_RATINGS,
        val seenFilter: SeenFilter = SeenFilter.ALL,
        val sortBy: SortBy = SortBy.ORIGINAL,
        val sortOrder: SortOrder = SortOrder.ASC
    ) {
        fun isDefault(): Boolean {
            return allowedRatings == ALL_RATINGS &&
                    seenFilter == SeenFilter.ALL &&
                    sortBy == SortBy.ORIGINAL &&
                    sortOrder == SortOrder.ASC
        }
    }

    private enum class SeenFilter {
        ALL,
        OPENED,
        UNOPENED
    }

    private enum class SortBy {
        ORIGINAL,
        RATING,
        URL
    }

    private enum class SortOrder {
        ASC,
        DESC
    }

    companion object {
        const val EXTRA_FILE_URI = "extra_file_uri"
        const val EXTRA_FILE_NAME = "extra_file_name"

        private val ALL_RATINGS = enumValues<LinkRating>().toSet()
        private val RATING_SUMMARY_ORDER = listOf(
            LinkRating.NONE,
            LinkRating.VERY_GOOD,
            LinkRating.GOOD,
            LinkRating.OKAY,
            LinkRating.BAD,
            LinkRating.VERY_BAD
        )

        private const val STATE_ALLOWED_RATINGS = "state_allowed_ratings"
        private const val STATE_SEEN_FILTER = "state_seen_filter"
        private const val STATE_SORT_BY = "state_sort_by"
        private const val STATE_SORT_ORDER = "state_sort_order"

        private const val PREFS_LINK_FILTER = "links_activity_filter_preferences"
        private const val PREF_ALLOWED_RATINGS = "pref_allowed_ratings"
        private const val PREF_SEEN_FILTER = "pref_seen_filter"
        private const val PREF_SORT_BY = "pref_sort_by"
        private const val PREF_SORT_ORDER = "pref_sort_order"
    }
}
