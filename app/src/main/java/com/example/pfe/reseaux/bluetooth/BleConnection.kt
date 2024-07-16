package com.example.pfe.reseaux.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID


val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
val SENSORS_CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


@Suppress("DEPRECATION")
class BLEDeviceConnection @RequiresPermission("android.permission.BLUETOOTH_CONNECT") constructor(
    private val context: Context,
     private val bluetoothDevice: BluetoothDevice
){


    val services = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    private var gatt: BluetoothGatt? = null

    var isConnected=MutableStateFlow(false)
    var gattSuccess =MutableStateFlow(false)
    var characteristicExist =MutableStateFlow(false)

    var ecgValue=MutableStateFlow(20.0)
    var tempValue=0
    var bpmValue=0
    var rhValue=0
    var co2Value=0.0



    private val callback = object: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val connected = newState == BluetoothGatt.STATE_CONNECTED
            if (connected) {
                //read the list of services
                services.value = gatt.services
            }
            else{
                characteristicExist.value = false
                gattSuccess.value = false

            }

            isConnected.value = connected

        }
        @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            services.value = gatt.services
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val sensorsCharacteristic = service.getCharacteristic(SENSORS_CHARACTERISTIC_UUID)
                characteristicExist.value=(sensorsCharacteristic != null)
                setCharacteristicNotification(sensorsCharacteristic, true)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (characteristic.uuid == SENSORS_CHARACTERISTIC_UUID) {



                val byteBuffer = ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                //notification.value+=1
                ecgValue.value = byteBuffer.int.toDouble()
                tempValue = byteBuffer.int
                bpmValue = byteBuffer.int
                rhValue = byteBuffer.int
                co2Value = (byteBuffer.int).toDouble()/1000.0
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // This method is called when a notification is received

            if (characteristic.uuid == SENSORS_CHARACTERISTIC_UUID) {



                    val byteBuffer = ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                    ecgValue.value=(byteBuffer.int.toDouble() * 3.3 / 4095) * 10 / 11
                    tempValue = byteBuffer.int
                    bpmValue = byteBuffer.int
                    rhValue = byteBuffer.int
                    co2Value = (byteBuffer.int).toDouble()/1000.0




            }
        }
    }



    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun connect() {
        gatt = bluetoothDevice.connectGatt(context, false, callback)

    }
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun discoverServices() {
        val success = gatt?.discoverServices()
        gattSuccess.value = success!!
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun readSensorsValue() {
        val service = gatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(SENSORS_CHARACTERISTIC_UUID)
        characteristicExist.value=(characteristic != null)
        if (characteristic != null) {
            val success = gatt?.readCharacteristic(characteristic)
            Log.v("bluetooth1", "Read ECG status: $success")
        }
    }




    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        if (gatt == null) return

        // Enable or disable notifications for the characteristic
        gatt?.setCharacteristicNotification(characteristic, enable)

        // If the characteristic supports notifications, also enable the CCCD
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)
        }
    }

}