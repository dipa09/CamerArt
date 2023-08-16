package com.example.camerart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        val videos = mutableListOf<VideoType>()

        for( v in videoUris){
            val uri = v
            val thumbnail = createVideoThumb(this, v)
            val video = VideoType(v,thumbnail)
            videos.add(video)
        }

        //2 recyclerview per distinguere i casi di foto e video dato che devo passare
        //2 operazioni di onBindViewHodlder diverse. Era il modo che creava meno mal di testa
        imageRecyclerView = findViewById(R.id.imageRecyclerView)
        imageRecyclerView.layoutManager = GridLayoutManager(this, 3)
        imageGalleryAdapter = ImageGalleryAdapter(imageUris)
        imageRecyclerView.adapter = imageGalleryAdapter;

        videoRecyclerView = findViewById(R.id.videoRecyclerView)
        videoRecyclerView.layoutManager = GridLayoutManager(this, 3)
        //passo lista di data class VideoType per tenere traccia dell uri, nel caso vogliamo fare
        //un onClickListener
        videoGalleryAdapter = VideoGalleryAdapter(videos)
        videoRecyclerView.adapter = videoGalleryAdapter;
    }

    private fun getImages(): List<String> {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val imageUris = mutableListOf<String>()
        //uso use per non avere problemi con il cursor e chiuderlo in automatico quando finisce
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
        //ritorna lista di string con gli uri (sono String non URI)
        return imageUris
    }
    private fun getVideos(): List<String> {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val videoUris = mutableListOf<String>()

        //uso use per non avere problemi con il cursor e chiuderlo in automatico quando finisce
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
        //ritorna lista di string con gli uri (sono String non URI)
        return videoUris
    }

    private fun createVideoThumb(context: Context,path : String): Bitmap? {
        try {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(path)

            //nel return getScaledFrameAtTime() potrebbe migliorare la situazione dato che
            //frameAtTime prende un frame a full risoluzione
            return mediaMetadataRetriever.frameAtTime
        } catch (ex: Exception) {
            Toast
                .makeText(context, "Error retrieving bitmap", Toast.LENGTH_SHORT)
                .show()
        }
        return null

    }
}