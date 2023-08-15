package com.example.camerart

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.BitmapFactory
import androidx.core.net.toUri

class ImageGalleryAdapter(private val imageUris: List<String>) :
    RecyclerView.Adapter<ImageGalleryAdapter.ImageViewHolder>() {


    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUri = imageUris[position]
        val uri = imageUri.toUri()
        // Load and display the image from the URI
        //val bitmap = BitmapFactory.decodeFile(imageUri)
        holder.imageView.setImageURI(uri)

    }

    override fun getItemCount(): Int {
        return imageUris.size
    }
}

class VideoGalleryAdapter(private val videos: List<VideoType>) :
    RecyclerView.Adapter<VideoGalleryAdapter.ImageViewHolder>() {


    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val video = videos[position]
        val thumbnail = video.getVideoThumbnail()
        // Load and display the image from the URI
        //val bitmap = BitmapFactory.decodeFile(imageUri)
        holder.imageView.setImageBitmap(thumbnail)

    }

    override fun getItemCount(): Int {
        return videos.size
    }
}