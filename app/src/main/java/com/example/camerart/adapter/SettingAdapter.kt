package com.example.camerart.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.camerart.R
import com.example.camerart.model.Setting

class SettingAdapter(private val context: Context, private val settings: List<Setting>)
    : RecyclerView.Adapter<SettingAdapter.SettingViewHolder>() {

    class SettingViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.setting_item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.setting_item, parent,false)
        return SettingViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        val setting = settings[position]
        holder.textView.text = context.resources.getString(setting.stringResourceId)
    }

    override fun getItemCount(): Int { return settings.size }
}