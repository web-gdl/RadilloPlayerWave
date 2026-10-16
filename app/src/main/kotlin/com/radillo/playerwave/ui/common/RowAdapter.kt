package com.radillo.playerwave.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.radillo.playerwave.R
import com.radillo.playerwave.core.ui.ArtworkLoader
import com.radillo.playerwave.databinding.ItemRowBinding

/** Fila genérica de lista: canción, álbum, artista, carpeta o lista. */
data class Row(
    val key: String,
    val title: String,
    val subtitle: String,
    /** Carátula a cargar; null muestra solo [placeholder]. */
    val artUri: String?,
    @DrawableRes val placeholder: Int = R.drawable.tile_note,
    val highlighted: Boolean = false,
    val showMore: Boolean = true,
)

/**
 * Adaptador único para todas las listas (una sola vista de fila, diff en segundo plano con
 * `ListAdapter`). Las carátulas las carga [ArtworkLoader] (RGB_565, caché pequeña).
 */
class RowAdapter(
    private val onClick: (Int) -> Unit,
    private val onMore: ((Int) -> Unit)? = null,
    @DrawableRes private val moreIcon: Int = R.drawable.ic_more_vert,
) : ListAdapter<Row, RowAdapter.Holder>(Diff) {

    class Holder(val binding: ItemRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = Holder(binding)
        binding.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
        binding.rowMore.setImageResource(moreIcon)
        binding.rowMore.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMore?.invoke(pos)
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val b = holder.binding
        b.rowTitle.text = row.title
        b.rowSubtitle.text = row.subtitle
        b.rowSubtitle.visibleIf(row.subtitle.isNotEmpty())
        b.rowTitle.isActivated = row.highlighted
        b.rowTitle.setTextColor(
            b.root.context.getColor(if (row.highlighted) R.color.accent else R.color.text_primary),
        )
        b.rowMore.visibleIf(row.showMore && onMore != null)
        val size = b.rowArt.layoutParams.width
        ArtworkLoader.get(b.root.context).load(b.rowArt, row.artUri, size, row.placeholder)
    }

    private object Diff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
    }
}

/** Configuración común de las listas: tamaño fijo y sin animaciones de elementos (menos trabajo de GPU). */
fun RecyclerView.setupList(adapter: RecyclerView.Adapter<*>) {
    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
    setHasFixedSize(true)
    itemAnimator = null
    this.adapter = adapter
}
