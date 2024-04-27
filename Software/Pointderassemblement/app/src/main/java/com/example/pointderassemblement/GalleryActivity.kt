package com.example.pointderassemblement

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.room.Room
import com.example.pointderassemblement.ui.home.HomeFragment
import com.example.pointderassemblement.ui.home.filePathSon1
import com.example.pointderassemblement.ui.home.filePathSon2
import com.example.pointderassemblement.ui.home.filePathSon3
import com.example.pointderassemblement.ui.home.filePathSon4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class GalleryActivity : AppCompatActivity(), OnItemClickListener {

    private var mediaPlayer2: MediaPlayer? = null
    private lateinit var records : ArrayList<AudioRecord>
    private lateinit var mAdapter : Adapter
    private lateinit var db : AppDatabase
    private lateinit var recyclerview : RecyclerView

    private lateinit var editBar: View
    private lateinit var btnClose: ImageButton
    private lateinit var btnBack: ImageButton

    private lateinit var btnDelete : ImageButton
    private lateinit var btnRename : ImageButton
    private lateinit var btnAttribuer : ImageButton
    private lateinit var tvDelete : TextView
    private lateinit var tvRename : TextView
    private lateinit var tvAttribuer : TextView

    private lateinit var bottomSheet : LinearLayout
    private lateinit var bottomSheetBehavior : BottomSheetBehavior<LinearLayout>

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recyclerview = findViewById(R.id.recyclerview)
        records = ArrayList()

        mediaPlayer2 = MediaPlayer()
        btnRename = findViewById(R.id.btnEdit)
        btnAttribuer = findViewById(R.id.btnAttribuer)
        btnDelete = findViewById(R.id.btnDelete)
        tvRename = findViewById(R.id.tvEdit)
        tvDelete = findViewById(R.id.tvDelete)
        tvAttribuer = findViewById(R.id.tvAttribuer)

        editBar = findViewById(R.id.editBar)
        btnClose = findViewById(R.id.btnClose)
        btnBack = findViewById(R.id.btnback)

        bottomSheet=findViewById(R.id.bottomsheet)
        bottomSheetBehavior=BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "audioRecords"
        ).build()

        mAdapter = Adapter(records, this)


        recyclerview.apply{
            adapter = mAdapter
            layoutManager= LinearLayoutManager(context)
        }
        fetchAll()


        btnClose.setOnClickListener{
                leaveEditMode()
        }


        btnBack.setOnClickListener{
            super.onBackPressed()
        }

        btnDelete.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Supprimer ?")
            val nbRecords = records.count{it.isChecked}
            builder.setMessage("Etes vous sûr de vouloir supprimer $nbRecords enregistrement ?")

            builder.setPositiveButton("Supprimer") {_, _ ->
                val toDelete = records.filter { it.isChecked }.toTypedArray()
                GlobalScope.launch {
                    db.audioRecordDao().delete(toDelete)
                    runOnUiThread {
                        records.removeAll(toDelete)
                        mAdapter.notifyDataSetChanged()
                        leaveEditMode()
                    }
                }
            }

            builder.setNegativeButton("Annuler") {_, _ ->
                // it does nothing
            }

            val dialog = builder.create()
            dialog.show()
        }

        btnAttribuer.setOnClickListener {
            val options = arrayOf("SON 1", "SON 2", "SON 3", "SON 4")

            val builder = AlertDialog.Builder(this)
            builder.setTitle("Choisir une option")
            val record = records.filter { it.isChecked }.get(0)
            var filePath = record.filePath

            builder.setItems(options) { dialog, which ->

                when (which) {
                    0 -> {
                        filePathSon1 = ""
                        filePathSon1 = filePath

                    }

                    1 -> {
                        filePathSon2 = ""
                        filePathSon2 = filePath

                    }

                    2 -> {
                        filePathSon3 = ""
                        filePathSon3 = filePath

                    }

                    3 -> {
                        filePathSon4 = ""
                        filePathSon4 = filePath

                    }
                }
            }

            builder.setNegativeButton("Annuler") { _, _ ->

            }


            builder.show()
        }
        btnRename.setOnClickListener{
            val builder = AlertDialog.Builder(this)
            val dialogView = this.layoutInflater.inflate(R.layout.rename_layout, null)
            builder.setView(dialogView)
            val dialog = builder.create()
            dialog.show()

            val record = records.filter { it.isChecked }.get(0)
            val textInput = dialogView.findViewById<TextInputEditText>(R.id.filenameInput)
            textInput.setText(record.filename)

            dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
                val input = textInput.text.toString()
                if(input.isEmpty()){
                    Toast.makeText(this, "A name is required", Toast.LENGTH_LONG).show()
                }else{
                    record.filename = input
                    GlobalScope.launch {
                        db.audioRecordDao().update(record)
                        runOnUiThread {
                            mAdapter.notifyItemChanged(records.indexOf(record))
                            dialog.dismiss()
                            leaveEditMode()
                        }
                    }
                }
            }
        }
    }

    private fun leaveEditMode () {
        editBar.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        btnBack.visibility= View.VISIBLE
        records.map{ it.isChecked = false}
        mAdapter.setEditMode(false)
    }

    private fun disableRename () {
        btnRename.isClickable = false
        btnRename.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.grayDarkDisabled, theme)
        tvRename.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.grayDarkDisabled, theme))
    }
    private fun disableDelete () {
        btnDelete.isClickable = false
        btnDelete.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.grayDarkDisabled, theme)
        tvDelete.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.grayDarkDisabled, theme))
    }
    private fun disableAssign () {
        btnAttribuer.isClickable = false
        btnAttribuer.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.grayDarkDisabled, theme)
        tvAttribuer.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.grayDarkDisabled, theme))
    }
    private fun enableAssign () {
        btnAttribuer.isClickable = true
        btnAttribuer.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.grayDark, theme)
        tvAttribuer.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.grayDark, theme))
    }
    private fun enableRename () {
        btnRename.isClickable = true
        btnRename.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.grayDark, theme)
        tvRename.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.grayDark, theme))
    }
    private fun enableDelete () {
        btnDelete.isClickable = true
        btnDelete.backgroundTintList = ResourcesCompat.getColorStateList(resources, R.color.grayDark, theme)
        tvDelete.setTextColor(ResourcesCompat.getColorStateList(resources, R.color.grayDark, theme))
    }

    private fun fetchAll(){
        GlobalScope.launch {
            records.clear()
            var queryResult : List<AudioRecord> = db.audioRecordDao().getAll()
            records.addAll(queryResult)

            mAdapter.notifyDataSetChanged()
        }
    }

    override fun onItemClickListener(position: Int) {
        var audioRecord = records[position]
        if(mAdapter.isEditMode()){
            records[position].isChecked = !records[position].isChecked
            mAdapter.notifyItemChanged(position)
            var nbSelected = records.count{it.isChecked}
            when(nbSelected){
                0 -> {
                    disableRename()
                    disableDelete()
                    disableAssign()
                }
                1 -> {
                    enableDelete()
                    enableRename()
                    enableAssign()
                }
                else -> {
                    disableRename()
                    enableDelete()
                    disableAssign()
                }
            }
        }else{

            var filePath = audioRecord.filePath
            if(mediaPlayer2?.isPlaying == false){
                mediaPlayer2 = MediaPlayer()
                mediaPlayer2!!.apply{

                    setDataSource(filePath)
                    prepare()
                    setOnCompletionListener {
                        //release()
                    }
                }
            }
            playSong()

        }

    }

    private fun playSong(){
        if(mediaPlayer2?.isPlaying == false) {
            mediaPlayer2?.start()
        }
        else {
            mediaPlayer2?.pause()
            mediaPlayer2?.seekTo(0)
        }

    }

    override fun onItemLongClickListener(position: Int) {
        mAdapter.setEditMode(true)
        records[position].isChecked = !records[position].isChecked
        mAdapter.notifyItemChanged(position)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        btnBack.visibility= View.GONE
        if(mAdapter.isEditMode() && editBar.visibility == View.GONE){
            editBar.visibility = View.VISIBLE
            enableDelete()
            enableRename()
            enableAssign()
        }
    }
}