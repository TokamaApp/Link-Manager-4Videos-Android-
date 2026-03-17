package com.tokama.linkmanager.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tokama.linkmanager.R
import com.tokama.linkmanager.data.LinkEntry
import com.tokama.linkmanager.data.LinkRating
import java.util.Collections

class LinkAdapter(
    private val onClick: (LinkEntry) -> Unit,
    private val onLongClick: (LinkEntry) -> Unit,
    private val onBadgeClick: (LinkEntry) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<LinkAdapter.LinkViewHolder>() {

    private val items = mutableListOf<LinkEntry>()
    private val selectedKeys = mutableSetOf<String>()
    private val openedUrls = mutableSetOf<String>()

    fun submitList(entries: List<LinkEntry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    fun submitSelection(keys: Set<String>) {
        selectedKeys.clear()
        selectedKeys.addAll(keys)
        notifyDataSetChanged()
    }

    fun submitOpenedUrls(urls: Set<String>) {
        openedUrls.clear()
        openedUrls.addAll(urls)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        Collections.swap(items, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCurrentItems(): List<LinkEntry> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_link, parent, false)
        return LinkViewHolder(view)
    }

    override fun onBindViewHolder(holder: LinkViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indexText: TextView = itemView.findViewById(R.id.tvIndex)
        private val urlText: TextView = itemView.findViewById(R.id.tvUrl)
        private val ratingBadge: TextView = itemView.findViewById(R.id.tvRatingBadge)
        private val dragHandle: ImageView = itemView.findViewById(R.id.ivDragHandle)

        fun bind(item: LinkEntry, position: Int) {
            indexText.text = "${position + 1}."
            urlText.text = item.url

            val isSelected = selectedKeys.contains(selectionKey(item))
            val isOpened = openedUrls.contains(item.url.trim())

            val badgeText: String
            val colorRes: Int

            when (item.rating) {
                LinkRating.VERY_GOOD -> {
                    badgeText = itemView.context.getString(R.string.rating_very_good)
                    colorRes = R.color.ratingVeryGood
                }
                LinkRating.GOOD -> {
                    badgeText = itemView.context.getString(R.string.rating_good)
                    colorRes = R.color.ratingGood
                }
                LinkRating.OKAY -> {
                    badgeText = itemView.context.getString(R.string.rating_okay)
                    colorRes = R.color.ratingOkay
                }
                LinkRating.BAD -> {
                    badgeText = itemView.context.getString(R.string.rating_bad)
                    colorRes = R.color.ratingBad
                }
                LinkRating.VERY_BAD -> {
                    badgeText = itemView.context.getString(R.string.rating_very_bad)
                    colorRes = R.color.ratingVeryBad
                }
                LinkRating.NONE -> {
                    badgeText = itemView.context.getString(R.string.rate_now)
                    colorRes = R.color.ratingNeutral
                }
            }

            ratingBadge.text = badgeText
            ratingBadge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(itemView.context, colorRes)
            )

            if (isOpened) {
                indexText.alpha = 0.45f
                urlText.alpha = 0.45f
                ratingBadge.alpha = 0.65f
                dragHandle.alpha = 0.65f
            } else {
                indexText.alpha = 1.0f
                urlText.alpha = 1.0f
                ratingBadge.alpha = 1.0f
                dragHandle.alpha = 1.0f
            }

            itemView.setBackgroundColor(
                if (isSelected) Color.parseColor("#243A7BFF") else Color.TRANSPARENT
            )

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }

            ratingBadge.setOnClickListener {
                onBadgeClick(item)
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

        private fun selectionKey(entry: LinkEntry): String {
            return "${entry.lineIndex}::${entry.url}"
        }
    }
}
