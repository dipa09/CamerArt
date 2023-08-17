package com.example.camerart

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
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
        //Log.d("XX", "Got ${videoUris.size} videos")

        val videos = mutableListOf<VideoType>()

        for( v in videoUris){
            val uri = v
            val thumbnail = createVideoThumb(this, v)
            val video = VideoType(v,thumbnail)
            videos.add(video)
        }

        //Log.d("XX", "Create video thumbs ${videos.size}")

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

        //Log.d("XX", "Imaeg Uris: ${imageUris.size} -- $imageUris")
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