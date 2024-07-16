package com.example.pfe.adapter

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pfe.R
import com.example.pfe.reseaux.bluetooth.ble_data1loaded
import com.example.pfe.reseaux.internet.internet_data1loaded
import com.example.pfe.reseaux.internet.internet_data2loaded
import com.example.pfe.reseaux.internet.internet_series3
import com.example.pfe.reseaux.wifi.wifi_data1loaded
import com.example.pfe.reseaux.wifi.wifi_data2loaded
import com.example.pfe.reseaux.wifi.wifi_series3
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.text.SimpleDateFormat
import java.util.Locale


class SavesAdapter(
    private val context: Context,
    private val names: MutableList<String>,
    private val series1: LineGraphSeries<DataPoint>,
    private val series2: LineGraphSeries<DataPoint>,
    private val series3: LineGraphSeries<DataPoint>,
    private var popupWindow: Dialog,
    private val reseaux: String,
    private var temp: TextView,
    private var bpm: TextView,
    private var rh: TextView,
    private var co2: TextView
) : RecyclerView.Adapter<SavesAdapter.ViewHolder>() {
    init {
        // Sort the names list alphabetically
        names.sort()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.itemName)
        val dateTextView: TextView = itemView.findViewById(R.id.date)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        var view=View(context)

        if(reseaux=="internet") {
            view = LayoutInflater.from(context).inflate(R.layout.internet_item_layout, parent, false)
        }
        else if(reseaux=="ble"){
            view = LayoutInflater.from(context).inflate(R.layout.ble_item_layout, parent, false)
        }
        else if(reseaux=="wifi"){
            view = LayoutInflater.from(context).inflate(R.layout.wifi_item_layout, parent, false)
        }

        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val name = names[position]
        holder.nameTextView.text = name

        val sharedPreferences = context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
        val key = "$reseaux _timestamp_$name"
        val now: Long = sharedPreferences.getLong(key, 0) // Retrieve the timestamp from SharedPreferences
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        holder.dateTextView.text = formatter.format(now)




        holder.itemView.setOnClickListener {
            // Load the data points for the clicked item
            loadDataForItem(names[position])
            if (popupWindow.isShowing) {
                popupWindow.dismiss()
            }
        }

        holder.deleteButton.setOnClickListener {

            // Disable the button to prevent further clicks
            holder.deleteButton.isEnabled = false

            val mSharedPreferences = context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
            val mEditor = mSharedPreferences.edit()

            mEditor.remove("$reseaux _dataPoints1_$name").apply()
            mEditor.remove("$reseaux _dataPoints2_$name").apply()
            mEditor.remove("$reseaux _temp_$name").apply()
            mEditor.remove("$reseaux _bpm_$name").apply()
            mEditor.remove("$reseaux _rh_$name").apply()
            mEditor.remove("$reseaux _co2_$name").apply()
            mEditor.remove("$reseaux _x_$name").apply()
            mEditor.remove("$reseaux _timestamp_$name").apply()

            // Update the list of saved names in SharedPreferences
            val savedNames = mSharedPreferences.getStringSet("$reseaux _savedNames", mutableSetOf())
            // Create a new set and add all elements from the retrieved set to the new set
            val newSavedNames = savedNames?.toMutableSet()
            // Remove the name you want to delete from the new set
            newSavedNames?.remove(name)
            // Put the new set back into the SharedPreferences
            mEditor.putStringSet("$reseaux _savedNames", newSavedNames).apply()

            names.removeAt(position)
            // Notify the adapter about the item removal
            notifyItemRemoved(position)
            // Notify the adapter about the item count change
            notifyItemRangeChanged(position, itemCount)
        }
    }



    private val originalNames = names.toMutableList()

    fun filter(query: String?) {
        val filteredNames = if (query.isNullOrEmpty()) {
            // If the query is empty, reset to the original list
            originalNames
        } else {
            // Otherwise, filter the list based on the query
            originalNames.filter { it.contains(query, ignoreCase = true) }
        }
        // Update the list displayed by the adapter
        names.clear()
        names.addAll(filteredNames)
        notifyDataSetChanged() // Notify the adapter that the data has changed
    }
    override fun getItemCount(): Int = names.size



    private fun loadDataForItem(name: String) {


        val sharedPreferences = context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
        val gson = Gson()

        when (reseaux) {
            "internet" -> {

                val dataPoints1Json = sharedPreferences.getString("$reseaux _dataPoints1_$name", "")
                val dataPoints2Json = sharedPreferences.getString("$reseaux _dataPoints2_$name", "")

                // Deserialize the JSON to your data structure
                val dataPoints11 = gson.fromJson<MutableList<DataPoint>>(dataPoints1Json,object : TypeToken<MutableList<DataPoint>>() {}.type)
                val dataPoints22 = gson.fromJson<MutableList<DataPoint>>(dataPoints2Json,object : TypeToken<MutableList<DataPoint>>() {}.type)

                val x = sharedPreferences.getInt("$reseaux _x_$name", 0)
                temp.text=sharedPreferences.getString("$reseaux _temp_$name","0")
                bpm.text=sharedPreferences.getString("$reseaux _bpm_$name","0")
                rh.text=sharedPreferences.getString("$reseaux _rh_$name","0")
                co2.text=sharedPreferences.getString("$reseaux _co2_$name","0")



                series1.resetData(dataPoints11.toTypedArray())
                series2.resetData(dataPoints22.toTypedArray())
                series3.resetData(arrayOf(DataPoint(x.toDouble()*0.01,0.0 ),DataPoint((x.toDouble()+1)*0.01,0.0 )))
                internet_data1loaded =true
                internet_data2loaded =true
                internet_series3=true
            }
            "ble" -> {

                val dataPoints1Json = sharedPreferences.getString("$reseaux _dataPoints1_$name", "")

                // Deserialize the JSON to your data structure
                val dataPoints11 = gson.fromJson<MutableList<DataPoint>>(dataPoints1Json,object : TypeToken<MutableList<DataPoint>>() {}.type)

                val x = sharedPreferences.getInt("$reseaux _x_$name", 0)
                temp.text=sharedPreferences.getString("$reseaux _temp_$name","0")
                bpm.text=sharedPreferences.getString("$reseaux _bpm_$name","0")
                rh.text=sharedPreferences.getString("$reseaux _rh_$name","0")
                co2.text=sharedPreferences.getString("$reseaux _co2_$name","0")



                series1.resetData(dataPoints11.toTypedArray())
                ble_data1loaded = true
            }
            "wifi" -> {

                val dataPoints1Json = sharedPreferences.getString("$reseaux _dataPoints1_$name", "")
                val dataPoints2Json = sharedPreferences.getString("$reseaux _dataPoints2_$name", "")

                // Deserialize the JSON to your data structure
                val dataPoints11 = gson.fromJson<MutableList<DataPoint>>(dataPoints1Json,object : TypeToken<MutableList<DataPoint>>() {}.type)
                val dataPoints22 = gson.fromJson<MutableList<DataPoint>>(dataPoints2Json,object : TypeToken<MutableList<DataPoint>>() {}.type)

                val x = sharedPreferences.getInt("$reseaux _x_$name", 0)
                temp.text=sharedPreferences.getString("$reseaux _temp_$name","0")
                bpm.text=sharedPreferences.getString("$reseaux _bpm_$name","0")
                rh.text=sharedPreferences.getString("$reseaux _rh_$name","0")
                co2.text=sharedPreferences.getString("$reseaux _co2_$name","0")



                series1.resetData(dataPoints11.toTypedArray())
                series2.resetData(dataPoints22.toTypedArray())
                series3.resetData(arrayOf(DataPoint(x.toDouble()*0.01,0.0 ),DataPoint((x.toDouble()+1)*0.01,0.0 )))
                wifi_data1loaded = true
                wifi_data2loaded = true
                wifi_series3=true

            }
        }



        // Optionally, you might want to notify the user or update the UI in some way
    }


}