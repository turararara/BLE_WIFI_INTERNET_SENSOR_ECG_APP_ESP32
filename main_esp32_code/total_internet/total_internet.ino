//Librery
#include <Firebase_ESP_Client.h>
#include <WiFi.h>
#include <Wire.h>
#include <DHT.h>
#include <math.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"
#include "MAX30105.h"
#include "heartRate.h"

//WIFI
#define WIFI_NAME  "Xperia"
#define PASSWORD   "12345678"

//Firebase
#define API_KEY ""
#define DATABASE_URL ""
#define ECG_DATA_PATH "sensors/ecg"
#define SENSORS_DATA_PATH "sensors/other"

FirebaseData fbdo;      // handels data when there is a change on a specific data base note path
FirebaseAuth auth;      // used for authantification
FirebaseConfig config;  // used for configuration

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

double temperature = 0;
double humidity = 0;

//AD8323
#define ECG_PIN 35


//Variables
bool SignUpOk = false;
unsigned long t_hTemp = 0;
unsigned long sensor_temp = 0;
//sensors


//Functions
void connect_WiFi() {
  WiFi.mode(WIFI_STA);  //Optional
  WiFi.begin(WIFI_NAME, PASSWORD);
  Serial.println("\nConnecting");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(100);
  }
  Serial.println("\nConnected to the WiFi network");
  Serial.print("Local ESP32 IP: ");
  Serial.println(WiFi.localIP());
  Serial.println();
}
void connect_Firebase() {
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("signUp OK");
    SignUpOk = true;
  } else {
    Serial.printf("%s\n", config.signer.signupError.message.c_str());
  }
  config.token_status_callback = tokenStatusCallback;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}


void other_sensors_task(void* pvParameters) {

  while(1) {
    // CO2


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

    if ((millis() - t_hTemp) > 1000) {
      t_hTemp = millis();

      sensorValue = analogRead(MQ135_PIN);
      sensor_volt = sensorValue / 4098.0 * 3.3;
      RS_gas = ((3.3 * 10.0) / sensor_volt) - 10.0;
      ratio = RS_gas / R0;
      CO2_percentage = round(ratio * 10000) / 1000;

      temperature = dht.readTemperature() ;
      humidity = dht.readHumidity() ;
    }

    delay(10);  // Adjust delay as needed
  }
}

void send_to_firebase() {
  FirebaseJsonArray SENSOR_DATA;
  SENSOR_DATA.clear();
  if (!isnan(temperature)) { SENSOR_DATA.set("/[" + String(0) + "]", temperature); }
  SENSOR_DATA.set("/[" + String(1) + "]", beatAvg);
  if (!isnan(humidity)) { SENSOR_DATA.set("/[" + String(2) + "]", humidity); }
  if (!isinf(CO2_percentage)) { SENSOR_DATA.set("/[" + String(3) + "]", CO2_percentage); }
  Firebase.RTDB.set(&fbdo, SENSORS_DATA_PATH, &SENSOR_DATA);
}

void setup() {
  delay(100);
  Serial.begin(115200);

  //////////// Initialize sensor MAX30105
  particleSensor.begin(Wire, I2C_SPEED_FAST);  // Use default I2C port, 400kHz speed
  particleSensor.setup();                      // Configure sensor with default settings
  particleSensor.setPulseAmplitudeRed(0x0A);   // Turn Red LED to low to indicate sensor is running

  dht.begin();

  //Connecting to Wifi
  connect_WiFi();

  //setup firebase
  connect_Firebase();

  //setup extra
  xTaskCreatePinnedToCore(
    other_sensors_task,   /* Function that implements the task */
    "Other Sensors", /* Name of the task */
    10000,           /* Stack size in words */
    NULL,            /* Task input parameter */
    1,               /* Priority of the task */
    NULL,            /* Task handle */
    1                /* Core where the task should run */
  );
}

FirebaseJsonArray ECG_DATA;


void loop() {

  if (Firebase.ready() && SignUpOk) {
    //Creating the Json Array
    for (int i = 0; i < 200; i++) {
      ECG_DATA.set("/[" + String(i) + "]", analogRead(ECG_PIN));
      delay(10);
    }
    //publish data
    
    Firebase.RTDB.set(&fbdo, ECG_DATA_PATH, &ECG_DATA);
    send_to_firebase();
  }
}




