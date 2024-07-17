#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>
#include <DHT.h>
#include <math.h>
#include "MAX30105.h"
#include "heartRate.h"


//ble values
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLEServer* pServer = NULL;
BLECharacteristic* sensorCharacteristic = NULL;

bool deviceConnected = false;
bool oldDeviceConnected = false;
uint32_t value = 0;

class MyServerCallbacks : public BLEServerCallbacks {

  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
  };

  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
  }
};

//Max30105 values
MAX30105 particleSensor;

const byte RATE_SIZE = 4;  // Increase this for more averaging. 4 is good.
byte rates[RATE_SIZE];     // Array of heart rates
byte rateSpot = 0;
long lastBeat = 0;  // Time at which the last beat occurred
float beatsPerMinute = 0;
int beatAvg = 0;
long irValue = 0;
long delta = 0;
//MQ135 values
#define MQ135_PIN 34
#define R0 10000.0

float sensor_volt;
float sensorValue;
float RS_gas;
float ratio;
float CO2_percentage;

//DHT11 values
#define DHT11_PIN 13
#define DHTTYPE DHT11
DHT dht(DHT11_PIN, DHTTYPE);

int temperature = 0;
int humidity = 0;

//AD8323
#define ECG_PIN 35

//sensors
int sensors[5] = { 0, 1, 2, 3, 4 };  // /ecg  /temp /BPM /RH /CO2


//variable
unsigned long now = 10;

//Functions

void task_temp_bpm_co2_rh(void* pvParameters) {
  for (;;) {


    // BPM
    irValue = particleSensor.getIR();
    if (irValue > 50000) {
      if (checkForBeat(irValue) == true) {
        delta = millis() - lastBeat;
        lastBeat = millis();
        beatsPerMinute = 60 / (delta / 1000.0);
        if (beatsPerMinute < 255 && beatsPerMinute > 20) {
          rates[rateSpot++] = (byte)beatsPerMinute;
          rateSpot %= RATE_SIZE;
          beatAvg = 0;
          for (byte x = 0; x < RATE_SIZE; x++)
            beatAvg += rates[x];
          beatAvg /= RATE_SIZE;
          sensors[2] = beatAvg;
        }
      }
    }else{beatAvg=0;}

    if ((millis() - now) > 1000) {
      now = millis();
      // TEMP RH
      sensors[1] = dht.readTemperature();
      sensors[3] = dht.readHumidity();
      // CO2
      sensorValue = analogRead(MQ135_PIN);
      sensor_volt = sensorValue / 4098.0 * 3.3;
      RS_gas = ((3.3 * 10.0) / sensor_volt) - 10.0;
      ratio = RS_gas / R0;
      sensors[4] = static_cast<int>(ratio * 10000.0);
    }
    delay(10);  // Adjust delay as needed
  }
}


void setup() {
  Serial.begin(115200);


  //////////// Initialize sensor MAX30105
  particleSensor.begin(Wire, I2C_SPEED_FAST);  // Use default I2C port, 400kHz speed
  particleSensor.setup();                      // Configure sensor with default settings
  particleSensor.setPulseAmplitudeRed(0x0A);   // Turn Red LED to low to indicate sensor is running

  dht.begin();

  //////////// Create the BLE Device
  BLEDevice::init("ESP32");
  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  // Create the BLE Service
  BLEService* pService = pServer->createService(SERVICE_UUID);
  // Create a BLE Characteristic
  sensorCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_INDICATE);
  // Create a BLE Descriptor
  sensorCharacteristic->addDescriptor(new BLE2902());
  // Start the service
  pService->start();
  // Start advertising
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);  // set value to 0x00 to not advertise this parameter
  BLEDevice::startAdvertising();



  xTaskCreatePinnedToCore(
    task_temp_bpm_co2_rh, /* Function that implements the task */
    "CO2 and BPM Task",   /* Name of the task */
    10000,                /* Stack size in words */
    NULL,                 /* Task input parameter */
    1,                    /* Priority of the task */
    NULL,                 /* Task handle */
    1                     /* Core where the task should run */
  );
}
void loop() {
  // notify changed value



  if (deviceConnected) {
    sensors[0] = analogRead(ECG_PIN);
    sensorCharacteristic->setValue((uint8_t*)&sensors, sizeof(sensors));
    sensorCharacteristic->notify();
    delay(5);
    // bluetooth stack will go into congestion, if too many packets are sent, in 6 hours test i was able to go as low as 3ms
  }
  // disconnecting
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);                   // give the bluetooth stack the chance to get things ready
    pServer->startAdvertising();  // restart advertising
    Serial.println("start advertising");
    oldDeviceConnected = deviceConnected;
  }
  // connecting
  if (deviceConnected && !oldDeviceConnected) {
    // do stuff here on connecting
    oldDeviceConnected = deviceConnected;
    Serial.println("reconnect");
  }
}
