package com.example.pfe.reseaux.internet


import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pfe.MainActivity
import com.example.pfe.R
import com.example.pfe.adapter.SavesAdapter
import com.example.pfe.databinding.ActivityInternetDashboardBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

var internet_data1loaded =false
var internet_data2loaded =false
var internet_series3 =false

class InternetDashboard : AppCompatActivity() {

    private lateinit var binding : ActivityInternetDashboardBinding

    //connection setup
    private var ecgDatabase: DatabaseReference =FirebaseDatabase.getInstance().getReference("sensors/ecg")
    private var otherSensorsDatabase: DatabaseReference =FirebaseDatabase.getInstance().getReference("sensors/other")
    private lateinit var postListener: ValueEventListener

    // graph variables
    private var graphTimer = Timer()
    private lateinit var graph:GraphView
    private lateinit var series1: LineGraphSeries<DataPoint>
    private lateinit var series2: LineGraphSeries<DataPoint>
    private lateinit var series3: LineGraphSeries<DataPoint>
    private lateinit var series4: LineGraphSeries<DataPoint>
    private val dataPoints1 = mutableListOf<DataPoint>()
    private val dataPoints2 = mutableListOf<DataPoint>()
    private var state: State = State.Start
    private var isTaskRunning = false
    private val ecgValues = Array(200) { 0.0 }
    private var index=1
    private var yMin=0.0
    private var yMax=4.0
    private var xMin=0.0
    private var xMax=10.0
    private var y=0.0
    private var ux=0.0
    private var scaleModifier=0.1
    private var dis=0.0
    private var x=0.0
    // graph advent parameters
    private var maxDataPoints=1001
    private var graphPeriod:Long=10
    private var counter=1
    private var counterDivision=0.01
    private var pointLimit=999.0


    // temporary data storing
    private var sensors= arrayOf(0.0,0.0,0.0,0.0)//temp,bpm,rh,co2

    // local database setup
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var gson:Gson
    private val internet="internet"

    // life scope setup
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInternetDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buttonStart()

        startDataStore()
        graphStart()
        graphUpdate()
        dataBaseListener()


    }



    // buttons
    private fun buttonStart(){
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true){
            override fun handleOnBackPressed(){
                val intent = Intent(this@InternetDashboard, MainActivity::class.java)
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
            overridePendingTransition(R.anim.slide_in_up,R.anim.slide_out_up)
            finish()
        }
        binding.pauseResumeButton.setOnCheckedChangeListener { buttonView, _ ->
            isTaskRunning = buttonView.isChecked
            if(isTaskRunning){ resumeSensorUpdate()}
            else{pauseSensorUpdate()}
        }
        binding.reset.setOnClickListener {
            xMin=0.0
            xMax=10.0
            graph.viewport.setMinX(xMin)
            graph.viewport.setMaxX(xMax)
            yMin=0.0
            yMax=4.0
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0 ,10.0), false, 1)
            graph.addSeries(series4)
        }
        binding.unzoom.setOnClickListener {
            dis =yMax-yMin
            yMin-=dis*scaleModifier
            yMax+=dis*scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0 ,10.0), false, 1)
            graph.addSeries(series4)
        }
        binding.zoom.setOnClickListener {
            dis =yMax-yMin
            yMax -= dis*scaleModifier
            yMin += dis*scaleModifier

            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0, 10.0), false, 1)
            graph.addSeries(series4)

        }
        binding.up.setOnClickListener {
            dis=yMax-yMin
            yMin+=dis*scaleModifier
            yMax+=dis*scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0 ,10.0), false, 1)
            graph.addSeries(series4)
        }
        binding.down.setOnClickListener {
            dis=yMax-yMin
            yMin-=dis*scaleModifier
            yMax-=dis*scaleModifier
            graph.viewport.setMinY(yMin)
            graph.viewport.setMaxY(yMax)
            series4.appendData(DataPoint(10.0 ,10.0), false, 1)
            graph.addSeries(series4)
        }
        binding.savePopUp.setOnClickListener {
            showNamePrompt()
        }
        binding.dataPopUp.setOnClickListener {
            showPopupWindow()
        }


    }

    // local data storing
    private fun startDataStore(){
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
        editor.putString("$internet _dataPoints1_$name", gson.toJson(dataPoints1))
        editor.putString("$internet _dataPoints2_$name", gson.toJson(dataPoints2))
        editor.putString("$internet _temp_$name",binding.temp.text.toString())
        editor.putString("$internet _bpm_$name",binding.bpm.text.toString())
        editor.putString("$internet _rh_$name",binding.rh.text.toString())
        editor.putString("$internet _co2_$name",binding.co2.text.toString())
        editor.putInt("$internet _x_$name", x.toInt())
        editor.putLong("$internet _timestamp_$name", timestamp)

        // Add the name to the list of saved names
        val savedNames = sharedPreferences.getStringSet("$internet _savedNames", mutableSetOf()) ?: mutableSetOf()
        savedNames.add(name)

        // Convert savedNames to an immutable set before saving
        editor.putStringSet("$internet _savedNames", savedNames).apply()




    }
    private fun showPopupWindow() {
        // Inflate the popup layout
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.internet_popup_layout, LinearLayout(this),false)


        // Create a Dialog with the popup layout
        val popupWindow = Dialog(this)
        popupWindow.requestWindowFeature(Window.FEATURE_NO_TITLE) // Remove title bar
        popupWindow.setContentView(popupView)

        // Retrieve the RecyclerView from the popup layout
        val popupRecyclerView = popupView.findViewById<RecyclerView>(R.id.popupRecyclerView)
        popupRecyclerView.layoutManager = LinearLayoutManager(this)

        // Populate the RecyclerView with your data
        val savedNames = sharedPreferences.getStringSet("$internet _savedNames", mutableSetOf()) ?: mutableSetOf()
        val adapter = SavesAdapter(this,
            savedNames.toMutableList(),
            series1,
            series2,
            series3,
            popupWindow,
            internet,
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

    // graph work
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
        graph.viewport.isXAxisBoundsManual =true
        graph.viewport.setMinY(yMin)
        graph.viewport.setMaxY(yMax)
        graph.viewport.isYAxisBoundsManual = true
        // Set the grid color to transparent for the axis lines



        graph.gridLabelRenderer.gridColor = ContextCompat.getColor(this, R.color.white)
        graph.gridLabelRenderer.isHighlightZeroLines=false
        graph.viewport.backgroundColor=ContextCompat.getColor(this, R.color.darkerGreen)
        graph.gridLabelRenderer.padding=50


        graph.gridLabelRenderer.horizontalLabelsColor = ContextCompat.getColor(this, R.color.white)
        graph.gridLabelRenderer.labelHorizontalHeight=20
        graph.gridLabelRenderer.horizontalAxisTitle="(S)"
        graph.gridLabelRenderer.horizontalAxisTitleColor=ContextCompat.getColor(this, R.color.white)

        graph.gridLabelRenderer.verticalLabelsColor = ContextCompat.getColor(this, R.color.white)
        graph.gridLabelRenderer.labelVerticalWidth=30
        graph.gridLabelRenderer.verticalAxisTitle="(mV)"
        graph.gridLabelRenderer.verticalAxisTitleColor=ContextCompat.getColor(this, R.color.white)
    }
    private fun graphUpdate() {
        graphTimer.schedule(object : TimerTask() {
            override fun run() {
                if (!isTaskRunning) return
                resetGraphAfterLoad()

                runOnUiThread {
                    updatePoint()
                    x+=counter
                    ux = x * counterDivision
                }

            }
        },0,graphPeriod)
    }
    private fun resetGraphAfterLoad() {
        if(internet_data1loaded){
            internet_data1loaded=false
            series1.resetData(dataPoints1.toTypedArray())
        }
        if(internet_data2loaded){
            internet_data2loaded=false
            series2.resetData(dataPoints2.toTypedArray())
        }
        if(internet_series3){
            internet_series3=false
            series3.resetData(emptyArray())
        }
    }
    private fun updatePoint(){
        if(index<ecgValues.lastIndex){
            y=ecgValues[index]
            index++
        }
        else{
            y = ecgValues[ecgValues.lastIndex]
        }

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






    // sensors work
    private fun resumeSensorUpdate() {
        job = scope.launch {
            while (true) {
                binding.temp.text=sensors[0].toInt().toString() +" C°"
                binding.bpm.text=sensors[1].toInt().toString()
                binding.rh.text=sensors[2].toInt().toString() + " %"
                binding.co2.text= (sensors[3]).toString()+ " %"
                delay(1000)
            }
        }
    }
    private fun pauseSensorUpdate() {
        job?.cancel()
    }

    // firebase communication
    private fun dataBaseListener() {
        postListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                snapshot.children.forEachIndexed { index, childSnapshot ->
                    ecgValues[index]= (childSnapshot.getValue(Double::class.java)!!*3.3/4095)*10/11
                }
                index=0
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@InternetDashboard,"FAILED", Toast.LENGTH_SHORT).show()
            }
        }
        ecgDatabase.addValueEventListener(postListener)

        postListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEachIndexed { _, childSnapshot ->
                    val sensorLabel = childSnapshot.key?.toInt() // Getting the label of the sensor
                    val sensorValue = childSnapshot.getValue(Double::class.java)?: 0.0 // Retrieving the value, defaulting to 0.0 if null

                    // Adding the sensor value to the map using its label as the key
                    sensors[sensorLabel!!] = sensorValue
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@InternetDashboard,"FAILED", Toast.LENGTH_SHORT).show()
            }
        }
        otherSensorsDatabase.addValueEventListener(postListener)




    }


    override fun onDestroy() {
        super.onDestroy()
        // Stop the Timer to prevent it from running after the activity is destroyed
        ecgDatabase.removeEventListener(postListener)
        otherSensorsDatabase.removeEventListener(postListener)
        graphTimer.cancel()
        job?.cancel()

    }
    enum class State {
        Start, Routine1, Routine2
    }

}


