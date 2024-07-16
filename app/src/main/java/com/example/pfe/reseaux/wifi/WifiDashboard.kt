package com.example.pfe.reseaux.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pfe.MainActivity
import com.example.pfe.R
import com.example.pfe.adapter.SavesAdapter
import com.example.pfe.databinding.ActivityWifiDashboardBinding
import com.google.gson.Gson
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket
import java.util.Timer
import java.util.TimerTask


var wifi_data1loaded = false
var wifi_data2loaded = false
var wifi_series3 = false

class WifiDashboard : AppCompatActivity() {
    private lateinit var binding: ActivityWifiDashboardBinding

    // graph variables
    private var graphTimer = Timer()
    private lateinit var graph: GraphView
    private lateinit var series1: LineGraphSeries<DataPoint>
    private lateinit var series2: LineGraphSeries<DataPoint>
    private lateinit var series3: LineGraphSeries<DataPoint>
    private lateinit var series4: LineGraphSeries<DataPoint>
    private val dataPoints1 = mutableListOf<DataPoint>()
    private val dataPoints2 = mutableListOf<DataPoint>()
    private var state: State = State.Start
    private var isTaskRunning = false
    private var yMin = 0.0
    private var yMax = 4.0
    private var xMin = 0.0
    private var xMax = 10.0
    private var y = 1.0
    private var ux = 0.0
    private var scaleModifier = 0.1
    private var dis = 0.0
    private var x = 0.0
    // graph advent parameters
    private var maxDataPoints=1001
    private var graphPeriod:Long=10
    private var counter=1
    private var counterDivision=0.01
    private var pointLimit=999.0

    // local database setup
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var gson: Gson
    private val wifi = "wifi"

    // life scope setup
    private val scope = CoroutineScope(Dispatchers.Main)

    // Sensor variables
    private var fullLine="0"
    private var sensors = listOf("0", "0", "0", "0", "0")



    // Wifi objects and variables
    private var wifiName = "0"
    private var wifiIp = "0"
    private var connectionStarted = false
    private lateinit var socket: Socket
    private lateinit var reader: BufferedReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWifiDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)






        buttonStart()
        startDataStore()
        graphStart()
        graphUpdate()
        wifiPermissions()
        getNetworkInfo()


    }

    // Wifi code
    private fun wifiPermissions() {
        val allBlePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }

        val permissionsNeeded = allBlePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 101)
        }
    }
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun promptEnableLocation() {
        if (!isLocationEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Location Services Disabled")
                .setMessage("enable location for the app to work")
                .setPositiveButton("Enable") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    private fun getNetworkInfo() {
        if (!isLocationEnabled()) {
            promptEnableLocation()
            return
        }


        val wifiManager =
            this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val intWifiIp = wifiManager.dhcpInfo.gateway

        wifiName = wifiInfo.ssid

        val bytes = byteArrayOf(
            (intWifiIp and 0xFF).toByte(),
            (intWifiIp shr 8 and 0xFF).toByte(),
            (intWifiIp shr 16 and 0xFF).toByte(),
            (intWifiIp shr 24 and 0xFF).toByte()
        )
        wifiIp = InetAddress.getByAddress(bytes).hostAddress


    }

    // buttons code
    private fun buttonStart() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@WifiDashboard, MainActivity::class.java)
                startActivity(intent)
                if(Build.VERSION.SDK_INT >= 34){
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,R.anim.slide_in_up,R.anim.slide_out_up)
                }else{
                    overridePendingTransition(R.anim.slide_in_up,R.anim.slide_out_up)
                }
                finish()
            }
        })
        binding.goToMainActivity.setOnClickListener{
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            if(Build.VERSION.SDK_INT >= 34){
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,R.anim.slide_in_up,R.anim.slide_out_up)
            }else{
                overridePendingTransition(R.anim.slide_in_up,R.anim.slide_out_up)
            }
            finish()
        }
        binding.pauseResumeButton.setOnCheckedChangeListener { buttonView, _ ->
            isTaskRunning = buttonView.isChecked
            if (isTaskRunning) {
                resumeSensorUpdate()
            } else {
                pauseSensorUpdate()
            }
        }
        binding.reset.setOnClickListener {
            xMin = 0.0
            xMax = 10.0
            graph.viewport.setMinX(xMin)
            graph.viewport.setMaxX(xMax)
            yMin = 0.0
            yMax = 4.0
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0, 10.0), false, 1)
            graph.addSeries(series4)
        }
        binding.unzoom.setOnClickListener {
            dis = yMax - yMin
            yMin -= dis * scaleModifier
            yMax += dis * scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0, 10.0), false, 1)
            graph.addSeries(series4)
        }
        binding.zoom.setOnClickListener {
            dis = yMax - yMin
            yMax -= dis * scaleModifier
            yMin += dis * scaleModifier

            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0, 10.0), false, 1)
            graph.addSeries(series4)

        }
        binding.up.setOnClickListener {
            dis = yMax - yMin
            yMin += dis * scaleModifier
            yMax += dis * scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0, 10.0), false, 1)
            graph.addSeries(series4)
        }
        binding.down.setOnClickListener {
            dis = yMax - yMin
            yMin -= dis * scaleModifier
            yMax -= dis * scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0, 10.0), false, 1)
            graph.addSeries(series4)


        }
        binding.savePopUp.setOnClickListener {
            showNamePrompt()
        }
        binding.dataPopUp.setOnClickListener {
            showPopupWindow()
        }
    }

    // local data storing code
    private fun startDataStore() {
        sharedPreferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE)
        editor = sharedPreferences.edit()
        gson = Gson()
    }
    private fun showNamePrompt() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("give a name")

        val inputField = EditText(this)
        builder.setView(inputField)

        builder.setPositiveButton("OK") { dialog, _ ->
            val name = inputField.text.toString()
            saveInformation(name)

            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }
    @SuppressLint("MutatingSharedPrefs")
    private fun saveInformation(name: String) {
        val timestamp = System.currentTimeMillis()

        // Save the data with the name as a key
        editor.putString("$wifi _dataPoints1_$name", gson.toJson(dataPoints1))
        editor.putString("$wifi _dataPoints2_$name", gson.toJson(dataPoints2))
        editor.putString("$wifi _temp_$name", binding.temp.text.toString())
        editor.putString("$wifi _bpm_$name", binding.bpm.text.toString())
        editor.putString("$wifi _rh_$name", binding.rh.text.toString())
        editor.putString("$wifi _co2_$name", binding.co2.text.toString())
        editor.putInt("$wifi _x_$name", x.toInt())
        editor.putLong("$wifi _timestamp_$name", timestamp)

        // Add the name to the list of saved names
        val savedNames =
            sharedPreferences.getStringSet("$wifi _savedNames", mutableSetOf()) ?: mutableSetOf()
        savedNames.add(name)

        // Convert savedNames to an immutable set before saving
        editor.putStringSet("$wifi _savedNames", savedNames).apply()


    }
    private fun showPopupWindow() {
        // Inflate the popup layout
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.wifi_popup_layout, LinearLayout(this), false)


        // Create a Dialog with the popup layout
        val popupWindow = Dialog(this)
        popupWindow.requestWindowFeature(Window.FEATURE_NO_TITLE) // Remove title bar
        popupWindow.setContentView(popupView)

        // Retrieve the RecyclerView from the popup layout
        val popupRecyclerView = popupView.findViewById<RecyclerView>(R.id.popupRecyclerView)
        popupRecyclerView.layoutManager = LinearLayoutManager(this)

        // Populate the RecyclerView with your data
        val savedNames =
            sharedPreferences.getStringSet("$wifi _savedNames", mutableSetOf()) ?: mutableSetOf()
        val adapter = SavesAdapter(
            this,
            savedNames.toMutableList(),
            series1,
            series2,
            series3,
            popupWindow,
            wifi,
            binding.temp,
            binding.bpm,
            binding.rh,
            binding.co2
        )
        popupRecyclerView.adapter = adapter

        val searchView = popupView.findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText) // Filter the list based on the search query
                return false
            }
        })

        searchView.setQueryHint("Search names")
        searchView.setIconifiedByDefault(false)
        // Set up the exit button click listener
        popupView.findViewById<Button>(R.id.exitButton).setOnClickListener {
            popupWindow.dismiss()
        }


        // Show the popup window
        popupWindow.show()
    }

    // graph code
    private fun graphStart() {
        graph = binding.graph
        series1 = LineGraphSeries()
        series2 = LineGraphSeries()
        series3 = LineGraphSeries()
        series4 = LineGraphSeries()
        series1.color = ContextCompat.getColor(this, R.color.white)
        series2.color = ContextCompat.getColor(this, R.color.white)
        series3.color = ContextCompat.getColor(this, R.color.white)
        graph.addSeries(series1)
        graph.addSeries(series2)
        graph.addSeries(series3)
        graph.addSeries(series4)
        graph.viewport.isScalable = true
        graph.viewport.isScrollable = true
        graph.viewport.setMinX(xMin)
        graph.viewport.setMaxX(xMax)
        graph.viewport.isXAxisBoundsManual = true
        graph.viewport.setMinY(yMin)
        graph.viewport.setMaxY(yMax)
        graph.viewport.isYAxisBoundsManual = true
        // Set the grid color to transparent for the axis lines


        graph.gridLabelRenderer.gridColor = ContextCompat.getColor(this, R.color.white)
        graph.gridLabelRenderer.isHighlightZeroLines = false
        graph.viewport.backgroundColor = ContextCompat.getColor(this, R.color.darkerPurple)
        graph.gridLabelRenderer.padding = 50


        graph.gridLabelRenderer.horizontalLabelsColor = ContextCompat.getColor(this, R.color.white)
        graph.gridLabelRenderer.labelHorizontalHeight = 20
        graph.gridLabelRenderer.horizontalAxisTitle = "(S)"
        graph.gridLabelRenderer.horizontalAxisTitleColor =
            ContextCompat.getColor(this, R.color.white)

        graph.gridLabelRenderer.verticalLabelsColor = ContextCompat.getColor(this, R.color.white)
        graph.gridLabelRenderer.labelVerticalWidth = 30
        graph.gridLabelRenderer.verticalAxisTitle = "(mV)"
        graph.gridLabelRenderer.verticalAxisTitleColor = ContextCompat.getColor(this, R.color.white)
    }
    private fun graphUpdate() {
        graphTimer.schedule(object : TimerTask() {
            override fun run() {
                if (!isTaskRunning) return


                resetGraphAfterLoad()

                runOnUiThread {
                    updatePoint()
                    x += counter
                    ux = x * counterDivision
                }
            }
        }, 0, graphPeriod)
    }
    private fun checkAndSwitchState() {
        when {
            state == State.Start && x >= pointLimit -> {
                state = State.Routine1;x = 0.0
            }

            state == State.Routine1 && dataPoints1.isEmpty() -> {
                state = State.Routine2;x = 0.0
            }

            state == State.Routine2 && dataPoints2.isEmpty() -> {
                state = State.Routine1;x = 0.0
            }
        }
    }

    private fun resetGraphAfterLoad() {
        if (wifi_data1loaded) {
            wifi_data1loaded = false
            series1.resetData(dataPoints1.toTypedArray())
        }
        if (wifi_data2loaded) {
            wifi_data2loaded = false
            series2.resetData(dataPoints2.toTypedArray())
        }
        if (wifi_series3) {
            wifi_series3 = false
            series3.resetData(emptyArray())
        }
    }
    private fun updatePoint(){
        when (state) {
            State.Start -> {
                dataPoints1.add(DataPoint(ux, y))
                series1.appendData(DataPoint(ux, y), false, maxDataPoints)
                checkAndSwitchState()
            }
            State.Routine1 -> {
                dataPoints1.removeAt(0)
                series1.resetData(dataPoints1.toTypedArray())
                dataPoints2.add(DataPoint(ux, y))
                series2.resetData(dataPoints2.toTypedArray())
                checkAndSwitchState()
            }
            State.Routine2 -> {
                dataPoints2.removeAt(0)
                series2.resetData(dataPoints2.toTypedArray())
                dataPoints1.add(DataPoint(ux, y))
                series1.resetData(dataPoints1.toTypedArray())
                checkAndSwitchState()
            }
        }
        if (x == 0.0) {
            series3.resetData(arrayOf())
        } else {
            series3.appendData(DataPoint(ux, 0.0), false, 5)
        }
    }






    // sensors updating code
    private fun resumeSensorUpdate() {
        getNetworkInfo()

        if (wifiName == "\"ESP32\"") {
            connectionStarted = true
            scope.launch(Dispatchers.IO) {
                try {
                    socket = Socket(wifiIp, 8080)
                    reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    // Read data continuously in a loop
                    while (true) {
                        fullLine = reader.readLine()

                        // Split the line into separate values based on the comma delimiter
                        sensors = fullLine.split(",")



                        // Update the UI with the parsed values
                        runOnUiThread {
                            y = (sensors[0].toDouble()* 3.3 / 4095) * 10 / 11
                            binding.temp.text = sensors[1] +" C°"
                            binding.bpm.text = sensors[2]
                            binding.rh.text = sensors[3]+ " %"
                            binding.co2.text = sensors[4]+" %"
                        }
                        Thread.sleep(5)

                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    private fun pauseSensorUpdate() {
        if (connectionStarted) {
            socket.close()
            reader.close()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the Timer to prevent it from running after the activity is destroyed
        graphTimer.cancel()
        if (connectionStarted) {
            socket.close()
            reader.close()
        }

    }

    enum class State {
        Start, Routine1, Routine2
    }

}