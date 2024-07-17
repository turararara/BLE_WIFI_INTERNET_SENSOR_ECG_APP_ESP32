//Librery's
#include <WiFi.h>
#include <Wire.h>
#include <DHT.h>
#include "MAX30105.h"
#include "heartRate.h"
#include <WiFi.h>
#include <math.h>

const char* ssid = "ESP32";  // Name of the access point
WiFiServer server(8080);       // Server on port 80

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

//AD8323 values
#define ECG_PIN 35

//Variables
bool SignUpOk = false;
unsigned long now1 = 0;

// functions

// separated running task for sensor calculations
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
        }
      }
    }else{beatAvg=0;}


    if ((millis() - now1) > 1000) {
      now1 = millis();
      // TEMP RH
      temperature = round(dht.readTemperature() * 100) / 100;
      humidity = round(dht.readHumidity() * 100) / 100;
      // CO2
      sensorValue = analogRead(MQ135_PIN);
      sensor_volt = sensorValue / 4098.0 * 3.3;
      RS_gas = ((3.3 * 10.0) / sensor_volt) - 10.0;
      ratio = RS_gas / R0;
      CO2_percentage = round(ratio * 10000) / 1000;
    }
    delay(10);  // Adjust delay as needed
  }
}




void setup() {
  Serial.begin(115200);
  delay(1000);
  //////////// Initialize sensor MAX30105
  particleSensor.begin(Wire, I2C_SPEED_FAST);  // Use default I2C port, 400kHz speed
  particleSensor.setup();                      // Configure sensor with default settings
  particleSensor.setPulseAmplitudeRed(0x0A);   // Turn Red LED to low to indicate sensor is running

  dht.begin();


  // configuration of task
  xTaskCreatePinnedToCore(
    task_temp_bpm_co2_rh, /* Function that implements the task */
    "CO2 and BPM Task",   /* Name of the task */
    10000,                /* Stack size in words */
    NULL,                 /* Task input parameter */
    1,                    /* Priority of the task */
    NULL,                 /* Task handle */
    1                     /* Core where the task should run */
  );

  WiFi.mode(WIFI_AP);
  WiFi.softAP(ssid);
  Serial.print("AP IP address: ");
  Serial.println(WiFi.softAPIP());
  server.begin();
}



WiFiClient client = server.available();

void loop() {

  client = server.available();
  if (client) {

    while (client.connected()) {


      String dataToSend = String(analogRead(ECG_PIN)) + "," + String(temperature) + "," + String(beatAvg) + "," + String(humidity) + "," + String(CO2_percentage);

      // Send data to the client
      client.println(dataToSend);
      delay(5);
    }
    client.stop();  // Close the connection
  }
}
