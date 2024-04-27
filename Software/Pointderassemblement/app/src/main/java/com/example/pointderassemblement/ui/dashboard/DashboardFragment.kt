package com.example.pointderassemblement.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.pointderassemblement.databinding.FragmentDashboardBinding
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import com.example.pointderassemblement.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Date
import android.os.*
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.room.Room
import com.example.pointderassemblement.AppDatabase
import com.example.pointderassemblement.GalleryActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Text
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.UUID


class DashboardFragment : Fragment(), com.example.pointderassemblement.Timer.OnTimerTickListener {

    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    private var addressESP =""
    private lateinit var recorder: MediaRecorder

    private lateinit var btnRecord : ImageButton
    private lateinit var btnList : ImageButton
    private lateinit var btnDone : ImageButton
    private lateinit var btnDelete : ImageButton
    private lateinit var btnOk : MaterialButton
    private lateinit var btnCancel : MaterialButton


    private var isRecording = false
    private var isPaused = false

    private var dirpath = ""
    private var filename =""

    private var duration =""

    private lateinit var bottomSheetBehavior : BottomSheetBehavior<LinearLayout>
    private lateinit var bottomSheet : LinearLayout
    private lateinit var bottomSheetBG : View
    private lateinit var filenameInput : TextInputEditText
    private lateinit var tvTimer : TextView

    private lateinit var btnBattery : Button
    private lateinit var tvBattery : TextView


    private lateinit var timer: com.example.pointderassemblement.Timer
    private lateinit var vibrator: Vibrator

    private lateinit var db : AppDatabase


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return
        }

        /*val textView: TextView = binding.textDashboard
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }*/
        timer = com.example.pointderassemblement.Timer(this)
        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        db = Room.databaseBuilder(
            requireContext(),
            AppDatabase::class.java,
            "audioRecords"
        ).build()

        tvBattery = root.findViewById(R.id.tvBattery)
        btnBattery = root.findViewById(R.id.btnBattery)

        tvTimer = root.findViewById(R.id.tvTimer)
        btnRecord = root.findViewById(R.id.btnRecord)
        btnList = root.findViewById(R.id.btnList)
        btnDone = root.findViewById(R.id.btnDone)
        btnDelete = root.findViewById(R.id.btnDelete)
        btnOk = root.findViewById(R.id.btnOk)
        btnCancel = root.findViewById(R.id.btnCancel)
        bottomSheet = root.findViewById(R.id.bottomSheet)
        bottomSheetBG = root.findViewById(R.id.bottomSheetBG)
        filenameInput = root.findViewById(R.id.filenameInput)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        bottomSheetBehavior.peekHeight=0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        btnBattery.setOnClickListener{
            getBattery()
        }
        btnRecord.setOnClickListener {
            when {
                isPaused -> resumeRecorder()
                isRecording -> pauseRecorder()
                else -> startRecording()
            }
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        btnList.setOnClickListener {
            startActivity(Intent(requireContext(), GalleryActivity::class.java))
        }

        btnDone.setOnClickListener {
            stopRecorder();

            bottomSheetBehavior.state=BottomSheetBehavior.STATE_EXPANDED
            bottomSheetBG.visibility = View.VISIBLE
            filenameInput.setText(filename)
        }

        btnCancel.setOnClickListener{
            File("$dirpath$filename.mp3").delete()
            dismiss()
        }

        btnOk.setOnClickListener {
            dismiss()
            save()
            Toast.makeText(requireContext(), "Record saved", Toast.LENGTH_SHORT).show()
        }
        bottomSheetBG.setOnClickListener {
            File("$dirpath$filename.mp3").delete()
            dismiss()
        }
        btnDelete.setOnClickListener {
            stopRecorder();
            File("$dirpath$filename.mp3").delete()
            Toast.makeText(requireContext(), "Record deleted", Toast.LENGTH_SHORT).show()

        }
        btnDelete.isClickable = false
        btnDelete.setImageResource(R.drawable.ic_delete_disabled)
        return root
    }

    private fun getBattery(){
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // Adresse MAC
            if (deviceName == "Speakers"){
                addressESP = deviceHardwareAddress
            }
        }

        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(addressESP) // Adresse MAC de l'ESP32
        val socket: BluetoothSocket? = device?.createRfcommSocketToServiceRecord(UUID.fromString(
            0x001F.toString()
        ))

        socket?.use { bluetoothSocket ->
            // Connexion
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothSocket.connect()

            // Envoi de données
            val outputStream: OutputStream = bluetoothSocket.outputStream
            val message: ByteArray = "Hello ESP32".toByteArray()
            outputStream.write(message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun save(){
        val newFilename = filenameInput.text.toString()
        if(newFilename != filename){
            var newFile = File("$dirpath$newFilename.mp3")
            File("$dirpath$filename.mp3").renameTo(newFile)
        }

        var filePath = "$dirpath$newFilename.mp3"
        var timestamp = Date().time
        var ampsPath = "$dirpath$newFilename"

        try{
            var fos = FileOutputStream(ampsPath)
            var out = ObjectOutputStream(fos)
            //out.writeObject(amplitudes)
            fos.close()
            out.close()
        }catch (e :IOException){}

        var record = com.example.pointderassemblement.AudioRecord(
            newFilename,
            filePath,
            timestamp,
            duration,
            ampsPath
        )

        GlobalScope.launch {
            db.audioRecordDao().insert(record)
        }

    }
    private fun dismiss(){
        bottomSheetBG.visibility = View.GONE
        bottomSheetBehavior.state=BottomSheetBehavior.STATE_COLLAPSED
        hideKeyboard(filenameInput)
    }
    private fun hideKeyboard(view: View){
        val imm: InputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

        private fun stopRecorder(){
            recorder.apply{
                stop()
                release()
            }
            isPaused =false
            isRecording =false
            timer.stop()

            btnList.visibility = View.VISIBLE
            btnDone.visibility = View.GONE
            btnDelete.isClickable = false
            btnDelete.setImageResource(R.drawable.ic_delete_disabled)
            btnRecord.setImageResource(R.drawable.ic_record)
            tvTimer.text = "00:00.00"

        }


        private fun pauseRecorder(){
            recorder.pause()
            isPaused =true
            timer.pause()
            btnRecord.setImageResource(R.drawable.ic_record)
        }
        private fun resumeRecorder(){
            recorder.resume()
            isPaused =false
            btnRecord.setImageResource(R.drawable.ic_pause)
            timer.start()
        }
        private fun startRecording(){

            recorder = MediaRecorder()
            dirpath = "${requireContext().externalCacheDir?.absolutePath}/"
            var simpleDateFormat = SimpleDateFormat("yyyy.MM.DD_hh.mm.ss")
            var date = simpleDateFormat.format(Date())
            filename="audio_record_$date"

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile("$dirpath$filename.mp3")


                try {
                    prepare()
                }catch(e :IOException){}

                start()
            }
            isRecording =true
            isPaused =false

            timer.start()

            btnRecord.setImageResource(R.drawable.ic_pause)
            btnDelete.isClickable = true
            btnDelete.setImageResource(R.drawable.ic_delete)
            btnList.visibility = View.GONE
            btnDone.visibility = View.VISIBLE
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    override fun onTimerTick(duration: String) {
        tvTimer.text = duration
        this.duration = duration.dropLast(3)
    }
}