package com.example.camerart


import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView



class GalleryActivity : AppCompatActivity() {

    private lateinit var imageRecyclerView: RecyclerView
    private lateinit var imageGalleryAdapter: ImageGalleryAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)


        val imageUris = getImages()

        imageRecyclerView = findViewById(R.id.imageRecyclerView)
        imageRecyclerView.layoutManager = GridLayoutManager(this, 2)
        imageGalleryAdapter = ImageGalleryAdapter(this, imageUris)
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
        val cursor =

            contentResolver.query(
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

        //ritorna le ultime 5 immagini prese
        return imageUris.asReversed()
    }

}