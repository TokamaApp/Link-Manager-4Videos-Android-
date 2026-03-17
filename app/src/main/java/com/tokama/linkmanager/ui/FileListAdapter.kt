package com.tokama.linkmanager.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tokama.linkmanager.R
import com.tokama.linkmanager.data.SavedFile
import com.tokama.linkmanager.storage.FileRepository
import java.util.Collections
import java.util.Locale

class FileListAdapter(
    private val onClick: (SavedFile) -> Unit,
    private val onLongClick: (SavedFile) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    private val items = mutableListOf<SavedFile>()
    private var fileRepository: FileRepository? = null

    fun submitList(files: List<SavedFile>) {
        items.clear()
        items.addAll(files)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        Collections.swap(items, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCurrentItems(): List<SavedFile> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        if (fileRepository == null) {
            fileRepository = FileRepository(parent.context.applicationContext)
        }

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.tvFileName)
        private val uriText: TextView = itemView.findViewById(R.id.tvFileUri)
        private val dragHandle: ImageView = itemView.findViewById(R.id.ivDragHandle)

        fun bind(item: SavedFile) {
            val repository = fileRepository
                ?: FileRepository(itemView.context.applicationContext).also {
                    fileRepository = it
                }

            nameText.text = stripKnownExtension(item.displayName)
            uriText.text = repository.getReadablePath(
                uri = Uri.parse(item.uriString),
                fallbackDisplayName = item.displayName
            )

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }

            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }
        }

        private fun stripKnownExtension(fileName: String): String {
            val lower = fileName.lowercase(Locale.ROOT)
            return when {
                lower.endsWith(".lst") -> fileName.dropLast(4)
                lower.endsWith(".txt") -> fileName.dropLast(4)
                else -> fileName
            }
        }
    }
}
