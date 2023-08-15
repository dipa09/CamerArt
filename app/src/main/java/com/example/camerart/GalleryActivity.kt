package com.example.camerart

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView



class GalleryActivity : AppCompatActivity() {

    private lateinit var imageRecyclerView: RecyclerView
    private lateinit var imageGalleryAdapter: ImageGalleryAdapter
    private lateinit var videoRecyclerView: RecyclerView
    private lateinit var videoGalleryAdapter: VideoGalleryAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)




        val imageUris = getImages()
        val videoUris = getVideos()
        val videos = mutableListOf<VideoType>()

        for( v in videoUris){
            val uri = v
            val thumbnail = createVideoThumb(this, v)
            val video = VideoType(v,thumbnail)
            videos.add(video)
        }


        imageRecyclerView = findViewById(R.id.imageRecyclerView)
        imageRecyclerView.layoutManager = GridLayoutManager(this, 3)
        imageGalleryAdapter = ImageGalleryAdapter(imageUris)
        imageRecyclerView.adapter = imageGalleryAdapter;

        videoRecyclerView = findViewById(R.id.videoRecyclerView)
        videoRecyclerView.layoutManager = GridLayoutManager(this, 3)
        videoGalleryAdapter = VideoGalleryAdapter(videos)
        videoRecyclerView.adapter = videoGalleryAdapter;
    }

    private fun getImages(): List<String> {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val imageUris = mutableListOf<String>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (it.moveToNext()) {
                val imagePath = it.getString(columnIndex)
                imageUris.add(imagePath)
            }
        }

        return imageUris
    }
    private fun getVideos(): List<String> {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val videoUris = mutableListOf<String>()
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (it.moveToNext()) {
                val videoPath = it.getString(columnIndex)
                videoUris.add(videoPath)
            }
        }

        return videoUris
    }

    private fun createVideoThumb(context: Context,path : String): Bitmap? {
        try {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(path)
            return mediaMetadataRetriever.frameAtTime
        } catch (ex: Exception) {
            Toast
                .makeText(context, "Error retrieving bitmap", Toast.LENGTH_SHORT)
                .show()
        }
        return null

    }
}