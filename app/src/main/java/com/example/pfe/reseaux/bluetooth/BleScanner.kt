package com.example.pfe.reseaux.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow

//These fields are marked as API >= 31 in the Manifest class, so we can't use those without warning.
//So we create our own, which prevents over-suppression of the Linter


class BLEScanner(context: Context) {

    private val bluetooth = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager
        ?: throw Exception("Bluetooth is not supported by this device")

    val isScanning = MutableStateFlow(false)
    var debugMessage=MutableStateFlow("Initial message")
    var foundDevice = MutableStateFlow(false)
    var esp32Device: BluetoothDevice? = null


    private val scanner: BluetoothLeScanner
        get() = bluetooth.adapter.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result ?: return

            if (result.device.name == deviceName ) {
                //We have found what we're looking for. Save it for later.
                esp32Device=result.device
                foundDevice.value=true
            }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
        }
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning.value = false
            debugMessage.value="error"
        }
    }

    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    fun startScanning() {
        scanner.startScan(scanCallback)
        isScanning.value = true
        debugMessage.value="scanning"
    }
    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    fun stopScanning() {
        scanner.stopScan(scanCallback)
        isScanning.value = false
        debugMessage.value="stopped"
    }
}