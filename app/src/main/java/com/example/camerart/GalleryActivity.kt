package com.example.camerart


import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File


class GalleryActivity : AppCompatActivity() {

    private lateinit var imageRecyclerView: RecyclerView
    private lateinit var imageGalleryAdapter: ImageGalleryAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)


        val imageUris = getImages()
        val videoUris = getVideos()
        val mediaUris = sortMedia(imageUris,videoUris)

        imageRecyclerView = findViewById(R.id.imageRecyclerView)
        imageRecyclerView.layoutManager = GridLayoutManager(this, 2)
        imageGalleryAdapter = ImageGalleryAdapter(this, mediaUris)
        imageRecyclerView.adapter = imageGalleryAdapter;

    }

    private fun getImages(): List<String> {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val imageUris = mutableListOf<String>()
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        //uso use per non avere problemi con il cursor e chiuderlo in automatico quando finisce
        val cursor = contentResolver.query(
            collection,
            projection,
            null,
            null,
            null
            )
        cursor?.use {
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
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        //uso use per non avere problemi con il cursor e chiuderlo in automatico quando finisce
        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
            )
        cursor?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (it.moveToNext()) {
                val videoPath = it.getString(columnIndex)
                videoUris.add(videoPath)
            }
        }

        return videoUris
    }
    private fun sortMedia(imageList : List<String>, videoList : List<String>) : List<String>{
        val media = mutableListOf<String>()
        media.addAll(imageList)
        media.addAll(videoList)
        media.sortBy { val f = File(it)
        f.lastModified()}
        return media.asReversed()
    }
}