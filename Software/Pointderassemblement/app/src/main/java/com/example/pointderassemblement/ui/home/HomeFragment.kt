package com.example.pointderassemblement.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.pointderassemblement.MainActivity
import com.example.pointderassemblement.R
import com.example.pointderassemblement.databinding.ActivityMainBinding
import com.example.pointderassemblement.databinding.FragmentHomeBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.util.concurrent.Executors

// Variable globale
var filePathSon1 : String = ""
var filePathSon2 : String = ""
var filePathSon3 : String = ""
var filePathSon4 : String = ""

class HomeFragment : Fragment() {



    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Déclaration des MediaPlayer pour les 4 boutons pour jouer les sons
    private var mediaPlayerSon1: MediaPlayer? = null
    private var mediaPlayerSon2: MediaPlayer? = null
    private var mediaPlayerSon3: MediaPlayer? = null
    private var mediaPlayerSon4: MediaPlayer? = null


    //Déclaration des Audio... Pour l'utilisation du téléphone comme micro
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private lateinit var btnmicro : ToggleButton // bouton micro

    private var intBufferSize = 0
    private lateinit var shortAudioData: ShortArray
    private val intGain = 1 // peut etre utiliser pour augmenter le son en sortie
    private var isActive = false //micro actif ou non
    val exe = Executors.newSingleThreadExecutor() // pour executer la fonction threadLoop
    // fin partir telephone=micro

    //jouer en boucle
    private var boucle =false
    private lateinit var btncycle : ImageButton



    //Bouton son
    private lateinit var btnson1 : ImageButton
    private lateinit var btnson2 : ImageButton
    private lateinit var btnson3 : ImageButton
    private lateinit var btnson4 : ImageButton
    //Layout pour l'arrière des boutons (sert a rien ici)
    private lateinit var layout1 : LinearLayout
    private lateinit var layout2 : LinearLayout
    private lateinit var layout3 : LinearLayout
    private lateinit var layout4 : LinearLayout

    // switch pour le choix direct ou decalé
    private lateinit var switch : Switch
    private var direct = true

    // partie midro décalé
    private var isRecording = false

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var audioFilePath: String? = null
    // fin partie micro décalé

        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val homeViewModel =
                ViewModelProvider(this).get(HomeViewModel::class.java)

            _binding = FragmentHomeBinding.inflate(inflater, container, false)
            val root: View = binding.root

            // initialisation des mediaplayers
            mediaPlayerSon1 = MediaPlayer()
            mediaPlayerSon2 = MediaPlayer()
            mediaPlayerSon3 = MediaPlayer()
            mediaPlayerSon4 = MediaPlayer()

            //association var et id
            btncycle = root.findViewById(R.id.cycle)
            btnmicro = root.findViewById(R.id.micro)
            btnson1 = root.findViewById(R.id.btnson1)
            btnson2 = root.findViewById(R.id.btnson2)
            btnson3 = root.findViewById(R.id.btnson3)
            btnson4 = root.findViewById(R.id.btnson4)
            layout1 = root.findViewById(R.id.layout1)
            layout2 = root.findViewById(R.id.layout2)
            layout3 = root.findViewById(R.id.layout3)
            layout4 = root.findViewById(R.id.layout4)
            switch = root.findViewById(R.id.sw)

            audioFilePath = "${requireContext().externalCacheDir?.absolutePath}/recording.3gp"

            switch.setOnClickListener{
                direct = !direct
                if (isRecording){
                    stopRecordingAndPlay()

                }
                if(isActive){
                    isActive = false
                    stopAudio()
                }
                btnmicro.isChecked = false
                if (direct){
                    switch.setText("Direct")
                }else{
                    switch.setText("Décalé")
                }
            }



            btnson1.setOnClickListener{
                if(mediaPlayerSon1?.isPlaying == false){
                    mediaPlayerSon1 = MediaPlayer()
                    mediaPlayerSon1!!.apply{

                        setDataSource(filePathSon1)
                        prepare()

                    }
                }
                playSong1()

            }
            btnson2.setOnClickListener{
                if(mediaPlayerSon2?.isPlaying == false){
                    mediaPlayerSon2 = MediaPlayer()
                    mediaPlayerSon2!!.apply{

                        setDataSource(filePathSon2)
                        prepare()

                    }
                }
                playSong2()
            }
            btnson3.setOnClickListener{
                if(mediaPlayerSon3?.isPlaying == false){
                    mediaPlayerSon3 = MediaPlayer()
                    mediaPlayerSon3!!.apply{

                        setDataSource(filePathSon3)
                        prepare()

                    }
                }
                playSong3()
            }
            btnson4.setOnClickListener{
                if(mediaPlayerSon4?.isPlaying == false){
                    mediaPlayerSon4 = MediaPlayer()
                    mediaPlayerSon4!!.apply{

                        setDataSource(filePathSon4)
                        prepare()

                    }
                }
                playSong4()
            }

            btncycle.setOnClickListener{
                repeat()
            }



            btnmicro.setOnClickListener{
                useMic()
            }

            return root
        }



    @RequiresApi(Build.VERSION_CODES.Q)
    fun useMic() {
        if(direct) {
            if (!isActive) {
                isActive = true

                exe.execute { threadLoop() }
            } else if (isActive) {
                isActive = false
                stopAudio()
            }
        } else{ // décalé
            if (isRecording){
                stopRecordingAndPlay()
            }else{
                startRecording()
            }
        }
    }

    // -----------------------------------------------------Micro décalé
    private fun startRecording() {
        val previousRecordingFile = File(audioFilePath)
        if (previousRecordingFile.exists()) {
            previousRecordingFile.delete()
        }

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFilePath)
            prepare()
            start()
        }
        isRecording = true
    }

    private fun stopRecordingAndPlay() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        isRecording = false

        // Jouer l'enregistrement
        playRecording()
    }

    private fun playRecording() {
        player = MediaPlayer().apply {
            setDataSource(audioFilePath)
            prepare()
            start()
        }
    }
    // -----------------------------------------------------Fin Micro décalé

    // -----------------------------------------------------jouer en boucle ou non
    fun repeat(){
        boucle = !boucle
        if(boucle) {
            btncycle.setImageResource(R.drawable.ic_replay)
            Toast.makeText(requireContext(), "Replay", Toast.LENGTH_SHORT).show()

        }else {
            btncycle.setImageResource(R.drawable.ic_noreplay)
            Toast.makeText(requireContext(), "No replay", Toast.LENGTH_SHORT).show()

        }
    }

    // -----------------------------------------------------boutton son
    fun playSong1(){
        if(boucle) {

            mediaPlayerSon1!!.isLooping=true
        }else {

            mediaPlayerSon1!!.isLooping=false
        }
        if(mediaPlayerSon1?.isPlaying == false) {
            mediaPlayerSon1?.start()
        }
        else {
            mediaPlayerSon1?.pause()
            mediaPlayerSon1?.seekTo(0)
        }
    }

    fun playSong2(){
        if(boucle) {

            mediaPlayerSon2!!.isLooping=true
        }else {

            mediaPlayerSon2!!.isLooping=false
        }
        if(mediaPlayerSon2?.isPlaying == false) {
            mediaPlayerSon2?.start()
        }
        else {
            mediaPlayerSon2?.pause()
            mediaPlayerSon2?.seekTo(0)
        }
    }

    fun playSong3(){
        if(boucle) {

            mediaPlayerSon3!!.isLooping=true
        }else {

            mediaPlayerSon3!!.isLooping=false
        }
        if(mediaPlayerSon3?.isPlaying == false) {
            mediaPlayerSon3?.start()
        }
        else {
            mediaPlayerSon3?.pause()
            mediaPlayerSon3?.seekTo(0)
        }
    }

    fun playSong4(){
        if(boucle) {

            mediaPlayerSon4!!.isLooping=true
        }else {

            mediaPlayerSon4!!.isLooping=false
        }
        if(mediaPlayerSon4?.isPlaying == false) {
            mediaPlayerSon4?.start()
        }
        else {
            mediaPlayerSon4?.pause()
            mediaPlayerSon4?.seekTo(0)
        }
    }
    // -----------------------------------------------------FIN bouton son


    // -----------------------------------------------------Micro direct
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun threadLoop() {

        val intRecordSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        intBufferSize = AudioRecord.getMinBufferSize(
            intRecordSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        shortAudioData = ShortArray(intBufferSize)

        // demande de permission
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
            return
        }
        //initialisation : choix des sources, formats ...
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            intRecordSampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            intBufferSize
        )

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            intRecordSampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            intBufferSize,
            AudioTrack.MODE_STREAM
        )

        //Début
        audioTrack!!.setPlaybackRate(intRecordSampleRate)
        audioRecord!!.startRecording()
        audioTrack!!.play()
        while (isActive) {
            audioRecord!!.read(shortAudioData, 0, shortAudioData.size) // entrée audio
            for (i in 0 until shortAudioData.size) {
                if (shortAudioData[i] * intGain< Short.MAX_VALUE)
                    shortAudioData[i] = (shortAudioData[i] * intGain).toShort()
                else shortAudioData[i] = Short.MAX_VALUE
            }
            audioTrack!!.write(shortAudioData, 0, shortAudioData.size) // sortie audio
        }
    }


    private fun stopAudio() {
        try {

            audioTrack?.stop()
            audioRecord?.stop()
            audioRecord?.release()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onResume() {
        super.onResume()
        // Réinitialiser l'état des éléments cliquables ici
    }

    override fun onPause() {
        super.onPause()
        // Effectuer des opérations de nettoyage légères ici
    }

    override fun onStop() {
        super.onStop()
        // Effectuer des opérations de nettoyage plus lourdes ici
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        recorder?.release()
        player?.release()

    }




}