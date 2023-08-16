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
        // Luca: se passo il bitmap a imageView tramite setImageBitmap, le immagini ruotano 90 gradi
        //val bitmap = BitmapFactory.decodeFile(imageUri)
        holder.imageView.setImageURI(uri)

    }

    override fun getItemCount(): Int {
        return imageUris.size
    }
}
/*
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
        //non ho trovato altri modi decenti di prendere il thumbnail di un video oltre a questa funzione
        //non è efficente ma sembra funzionare a emulatore
        holder.imageView.setImageBitmap(thumbnail)

    }

    override fun getItemCount(): Int {
        return videos.size
    }


}

 */