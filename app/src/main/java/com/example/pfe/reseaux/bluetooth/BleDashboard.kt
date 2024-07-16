package com.example.pfe.reseaux.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pfe.MainActivity
import com.example.pfe.R
import com.example.pfe.adapter.SavesAdapter
import com.example.pfe.databinding.ActivityBleDashboardBinding
import com.google.gson.Gson
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

var ble_data1loaded = false
var deviceName = "ESP32"


class BleDashboard : AppCompatActivity() {
    private lateinit var binding: ActivityBleDashboardBinding

    //connection setup
    private lateinit var bleScanner: BLEScanner
    private lateinit var bleDeviceConnection: BLEDeviceConnection
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private var isBleDeviceConnectionInitialized = false
    private var reconnectTimer = 0

    // graph setup
    private var graphTimer = Timer()
    private lateinit var graph: GraphView
    private lateinit var series1: LineGraphSeries<DataPoint>
    private lateinit var series2: LineGraphSeries<DataPoint>
    private lateinit var series3: LineGraphSeries<DataPoint>
    private lateinit var series4: LineGraphSeries<DataPoint>
    private var isTaskRunning = false
    private var ux = 0.0
    private var y = 0.0
    private var yMin = 0.0
    private var yMax = 4.0
    private var xMin = 0.0
    private var xMax = 10.0
    private var scaleModifier = 0.1
    private var dis = 0.0
    // graph advent parameters
    private var maxDataPoints=1001
    private var graphPeriod:Long=10
    private var counter=1
    private var counterDivision=0.01
    private var infoChanged=false

    // storing data
    private var x = 0.0
    private var dataPoints1 = mutableListOf<DataPoint>()
    private val dataPoints2 = mutableListOf<DataPoint>()

    // temporary storing
    private var tempStock = 0
    private var bpmStock = 0
    private var rhStock = 0
    private var co2Stock = 0.0

    // local database setup
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var gson: Gson
    private val ble = "ble"

    // life scope setup
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)




    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBleDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buttonStart()
        startDataStore()
        graphStart()
        blePermissions()
        bluetoothCheck()

    }

    // buttons code
    private fun buttonStart() {

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@BleDashboard, MainActivity::class.java)
                startActivity(intent)
                if (Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(
                        OVERRIDE_TRANSITION_CLOSE,
                        R.anim.slide_in_up,
                        R.anim.slide_out_up
                    )
                } else {
                    overridePendingTransition(R.anim.slide_in_up, R.anim.slide_out_up)
                }
                finish()
            }
        })
        binding.goToMainActivity.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_CLOSE,
                    R.anim.slide_in_up,
                    R.anim.slide_out_up
                )
            } else {
                overridePendingTransition(R.anim.slide_in_up, R.anim.slide_out_up)
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
            yMin = 0.0
            yMax = 4.0
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            graph.viewport.scrollToEnd()
            series4.appendData(DataPoint(0.0, 0.0), false, 1)
            graph.addSeries(series4)
        }
        binding.unzoom.setOnClickListener {
            dis = yMax - yMin
            yMin -= dis * scaleModifier
            yMax += dis * scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(0.0, 0.0), false, 1)
            graph.addSeries(series4)
        }
        binding.zoom.setOnClickListener {
            dis = yMax - yMin
            yMax -= dis * scaleModifier
            yMin += dis * scaleModifier

            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(0.0, 0.0), false, 1)
            graph.addSeries(series4)

        }
        binding.up.setOnClickListener {
            dis = yMax - yMin
            yMin += dis * scaleModifier
            yMax += dis * scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(0.0, 0.0), false, 1)
            graph.addSeries(series4)
        }
        binding.down.setOnClickListener {
            dis = yMax - yMin
            yMin -= dis * scaleModifier
            yMax -= dis * scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(0.0, 0.0), false, 1)
            graph.addSeries(series4)
        }
        binding.savePopUp.setOnClickListener {
            showNamePrompt()
        }
        binding.dataPopUp.setOnClickListener {
            showPopupWindow()
        }
    }

    // local data storage code
    private fun startDataStore() {
        sharedPreferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE)
        editor = sharedPreferences.edit()
        gson = Gson()
    }
    private fun showNamePrompt() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Give a name")

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

        editor.putString("$ble _dataPoints1_$name", gson.toJson(dataPoints1))
        editor.putString("$ble _dataPoints2_$name", gson.toJson(dataPoints2))
        editor.putString("$ble _temp_$name", binding.temp.text.toString())
        editor.putString("$ble _bpm_$name", binding.bpm.text.toString())
        editor.putString("$ble _rh_$name", binding.rh.text.toString())
        editor.putString("$ble _co2_$name", binding.co2.text.toString())
        editor.putInt("$ble _x_$name", x.toInt())


        val timestamp = System.currentTimeMillis()
        editor.putLong("$ble _timestamp_$name", timestamp)


        // Add the name to the list of saved names
        val savedNames =
            sharedPreferences.getStringSet("$ble _savedNames", mutableSetOf()) ?: mutableSetOf()
        savedNames.add(name)

        // Convert savedNames to an immutable set before saving
        editor.putStringSet("$ble _savedNames", savedNames)
        editor.apply()
    }
    private fun showPopupWindow() {
        // Inflate the popup layout
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.ble_popup_layout, LinearLayout(this), false)


        // Create a Dialog with the popup layout
        val popupWindow = Dialog(this)
        popupWindow.requestWindowFeature(Window.FEATURE_NO_TITLE) // Remove title bar
        popupWindow.setContentView(popupView)

        // Retrieve the RecyclerView from the popup layout
        val popupRecyclerView = popupView.findViewById<RecyclerView>(R.id.popupRecyclerView)
        popupRecyclerView.layoutManager = LinearLayoutManager(this)

        // Populate the RecyclerView with your data
        val savedNames =
            sharedPreferences.getStringSet("$ble _savedNames", mutableSetOf()) ?: mutableSetOf()
        val adapter = SavesAdapter(
            this,
            savedNames.toMutableList(),
            series1,
            series2,
            series3,
            popupWindow,
            ble,
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
        graph.viewport.backgroundColor = ContextCompat.getColor(this, R.color.darkerBlue)
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
            @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
            override fun run() {
                if (!isTaskRunning) return
                handleBleConnectionLoss()
                resetGraphAfterLoad()
                runOnUiThread {
                    x+=counter
                    ux = x*counterDivision
                    reconnectTimer++

                    if (infoChanged) {
                        infoChanged=false
                        series1.appendData(DataPoint(ux, y), true, maxDataPoints)
                        if(x<1000){
                            dataPoints1.add(DataPoint(ux, y))
                        }else if(x>1000){
                            dataPoints1.removeAt(0)
                            dataPoints1.add(DataPoint(ux, y))
                        }
                    }
                }




            }
        }, 0, graphPeriod) // Update every 10 ms
    }
    private fun resetGraphAfterLoad() {
        if (ble_data1loaded) {
            ble_data1loaded = false
            series1.resetData(dataPoints1.toTypedArray())
        }
    }




    // ble functions
    private fun blePermissions() {
        val allBlePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }


        val permissionsNeeded = allBlePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 100)
        } else {
            // All permissions are already granted
            /* CAN BE USED TO SEE IF ALL PERMISSIONS ARE GRANTED
            findViewById<TextView>(R.id.message).text = (ALL_BLE_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED}).toString()
            */
        }


    }
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    private fun bluetoothCheck() {
        enableBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    // Bluetooth has been enabled
                    //Toast.makeText(this, "Bluetooth has been enabled", Toast.LENGTH_SHORT).show()
                    startBluetoothOperations()
                    // Proceed with your logic here
                } else {
                    // Bluetooth has not been enabled
                    Toast.makeText(this, "Bluetooth has not been enabled", Toast.LENGTH_SHORT)
                        .show()
                    // Handle the case where Bluetooth was not enabled
                }
            }
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (!bluetoothAdapter.isEnabled) {
            // Bluetooth is not enabled, request to enable it
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)

        } else {
            // Bluetooth is already enabled, proceed with your logic here
            //Toast.makeText(this, "Bluetooth is already enabled", Toast.LENGTH_SHORT).show()
            startBluetoothOperations()
        }
    }
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    private fun startBluetoothOperations() {
        bleScanner = BLEScanner(this)
        bleScanner.startScanning()
        graphUpdate()
        startConnection()
    }
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    private fun startConnection() {
        lifecycleScope.launch {
            bleScanner.foundDevice.collect {
                if (bleScanner.esp32Device?.name == deviceName) {
                    bleDeviceConnection =
                        BLEDeviceConnection(this@BleDashboard, bleScanner.esp32Device!!)
                    bleDeviceConnection.connect()
                    delay(1000L)
                    bleDeviceConnection.discoverServices()
                    delay(1000L)
                    isBleDeviceConnectionInitialized = true
                    bleScanner.stopScanning()
                    valueUpdater()
                }
            }
        }
    }
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private fun handleBleConnectionLoss() {
        if (isBleDeviceConnectionInitialized) {
            if (!(bleDeviceConnection.isConnected.value && bleDeviceConnection.gattSuccess.value && bleDeviceConnection.characteristicExist.value)) {
                if (reconnectTimer > 500) {
                    reconnectTimer = 0
                    lifecycleScope.launch {
                        Toast.makeText(this@BleDashboard, "Trying to reconnect", Toast.LENGTH_SHORT)
                            .show()
                        bleDeviceConnection.disconnect()
                        delay(1000L)
                        bleDeviceConnection.connect()
                        delay(1000L)
                        bleDeviceConnection.discoverServices()
                        delay(1000L)
                    }
                }
                y = 0.0
            }
        } else {
            y = 3.0
        }

    }
    @SuppressLint("SetTextI18n")
    private fun valueUpdater() {
        // sensor info
        lifecycleScope.launch {
            bleDeviceConnection.ecgValue.collect { ecgValue ->


                y=ecgValue
                infoChanged=true
                tempStock=bleDeviceConnection.tempValue
                bpmStock=bleDeviceConnection.bpmValue
                rhStock=bleDeviceConnection.rhValue
                co2Stock=bleDeviceConnection.co2Value
            }
        }



        /*debuginfo
        lifecycleScope.launch { bleScanner.isScanning.collect{ isScanning ->
            binding.SCAN.text = "SCAN: $isScanning"
        }}
        lifecycleScope.launch { bleDeviceConnection.isConnected.collect { isConnected ->
            binding.connection.text = "Connection: $isConnected"
        }}
        lifecycleScope.launch { bleDeviceConnection.gattSuccess.collect { gattSuccess ->
            binding.gattSuccess.text = "GATT Existence: $gattSuccess"
        }}
        lifecycleScope.launch { bleDeviceConnection.characteristicExist.collect { characteristicExist ->
            binding.characteristic.text = "Characteristic Existence: $characteristicExist"
        }}
        */
    }

    // sensors code
    private fun resumeSensorUpdate() {
        job = scope.launch {
            while (true) {
                binding.temp.text = tempStock.toString()+" C°"
                binding.bpm.text = bpmStock.toString()
                binding.rh.text = rhStock.toString()+ " %"
                binding.co2.text = co2Stock.toString()+ " %"
                delay(1000)
            }
        }
    }
    private fun pauseSensorUpdate() {
        job?.cancel()
    }


    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    override fun onDestroy() {
        super.onDestroy()
        // Stop the Timer to prevent it from running after the activity is destroyed
        if (isBleDeviceConnectionInitialized) {
            bleDeviceConnection.disconnect()
        }
        graphTimer.cancel()
    }



}



