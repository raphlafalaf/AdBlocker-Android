package com.dev.adblocker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView

class DomainChipAdapter(
    context: Context,
    private val items: List<String>,
    private val onRemove: (String) -> Unit,
) : ArrayAdapter<String>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_domain_chip, parent, false)
        val domain = items[position]
        v.findViewById<TextView>(R.id.tvDomain).text = domain
        v.findViewById<ImageButton>(R.id.btnRemove).setOnClickListener { onRemove(domain) }
        return v
    }
}
