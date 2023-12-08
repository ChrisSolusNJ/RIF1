package com.chrisnj.rif

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.chrisnj.rif.databinding.ActivityMainBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var database: DatabaseReference
    private val SPEECH_REQUEST_CODE = 123
    private val CHANNEL_ID = "mi_canal_de_notificaciones"
    private val NOTIFICATION_ID = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar el cambio de estado del Switch
        binding.buttonPower.setOnCheckedChangeListener { _, isChecked ->
            setData(isChecked)
        }

        // Configurar el botón para la entrada de voz
        binding.buttonVoice.setOnClickListener {
            activityReconoce()
        }

        binding.buttonSetTiempo.setOnClickListener {
            guardarTiempoEnFirebase()
        }

        // Configurar el OnClickListener para el botón Limpiar
        binding.buttonLimpiar.setOnClickListener {
            // Limpiar el contenido de editTextTiempo
            binding.editTextTiempo.text.clear()
        }

        // Configurar el cambio en la ruta /SensorLiquido/lleno
        val llenoReference = FirebaseDatabase.getInstance().getReference("SensorLiquido").child("lleno")
        llenoReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val llenoValue = snapshot.getValue(Int::class.java)
                if (llenoValue == 1) {
                    enviarNotificacion("El agua está por acabarse, recuerda llenarla")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error al obtener información de lleno", Toast.LENGTH_SHORT).show()
            }
        })

        databaseListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        restablecerValoresFirebase()
    }

    private fun restablecerValoresFirebase() {
        // Restablecer los valores en Firebase al cerrar la aplicación
        val firebaseReference = FirebaseDatabase.getInstance().getReference("BOMBA/digital")
        firebaseReference.setValue(false)

        val tiempoReference = FirebaseDatabase.getInstance().getReference("Temporizador")
        tiempoReference.child("tiempo").setValue(0)
        tiempoReference.child("Iniciar").setValue(false)
    }

    private fun guardarTiempoEnFirebase() {
        var tiempoIngresado: Int = 0
        var tiempoSegundos: Int = 0
        try {
            tiempoIngresado = binding.editTextTiempo.text.toString().toInt()
            tiempoSegundos = tiempoIngresado * 60;
        }catch (e:Exception){
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("Temporizador")
        database.child("tiempo").setValue(tiempoSegundos).addOnSuccessListener {
            Toast.makeText(this, "Se guardo correctamente", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "No se guardo correctamente", Toast.LENGTH_SHORT).show()
        }
        database.child("Iniciar").setValue(true)

    }

    // Crear un intent que pueda iniciar la actividad de Speech Recognizer
    private fun activityReconoce() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        startActivityForResult(intent, SPEECH_REQUEST_CODE)
    }

    // Esta devolución de llamada se invoca cuando vuelve Speech Recognizer.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val textoSpeech: String? = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            //binding.textview.text = textoSpeech
            sendFirebaseCommand(textoSpeech)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun sendFirebaseCommand(textoSpeech: String?) {
        val firebaseReference = FirebaseDatabase.getInstance().getReference("BOMBA/digital")

        when (textoSpeech?.toLowerCase()) {
            "encendido" -> firebaseReference.setValue(true)
            "encender" -> firebaseReference.setValue(true)
            "encender bomba" -> firebaseReference.setValue(true)
            "apagado" -> firebaseReference.setValue(false)
            "apagar" -> firebaseReference.setValue(false)
            "apagar bomba" -> firebaseReference.setValue(false)
        }
    }

    private fun setData(isSwitchChecked: Boolean) {
        database = FirebaseDatabase.getInstance().getReference("BOMBA")

        // Establece el valor en Firebase según el estado del Switch
        database.child("digital").setValue(isSwitchChecked).addOnSuccessListener {
            val mensaje = if (isSwitchChecked) "Bomba Digital encendida" else "Bomba Digital apagada"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Error al cambiar el estado de la Bomba Digital", Toast.LENGTH_SHORT).show()
        }
    }

    private fun databaseListener() {
        database = FirebaseDatabase.getInstance().getReference()
        val postListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = snapshot.child("Sensor/temp").value
                val humedad = snapshot.child("Sensor/sensor_data").value

                // Actualiza las ProgressBar con los valores de temperatura y humedad
                binding.progressBarTemp.progress = temp.toString().toFloat().toInt()
                binding.progressBarHumedad.progress = humedad.toString().toFloat().toInt()

                binding.textSensorData.text = humedad.toString()
                binding.textTempData.text = temp.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error al obtener información", Toast.LENGTH_SHORT).show()
            }
        }
        database.addValueEventListener(postListener)
    }
    private fun enviarNotificacion(mensaje: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Crear el canal de notificación (necesario a partir de Android 8.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Mi Canal de Notificaciones", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        // Crear y mostrar la notificación
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aviso de llenado")
            .setContentText(mensaje)
            .setSmallIcon(R.drawable.ic_notification) // Reemplaza esto con el ícono de tu aplicación
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
