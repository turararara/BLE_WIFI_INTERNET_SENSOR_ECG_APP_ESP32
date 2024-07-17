#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <SPI.h>
#include <LoRa.h>

#define ss D8
#define rst D0
#define dio0 D2

#define SCREEN_WIDTH 128 // Screen width
#define SCREEN_HEIGHT 64 // Screen height
#define OLED_RESET    -1 // Screen doesn't need a reset

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
int ecgPin = A0; // Specify the analog input for ECG

void setup() {
  Serial.begin(115200);
  Wire.begin(D4,D3);
  
  // Initialize the screen
  if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
    Serial.println(F("SSD1306 allocation failed"));
    for(;;);
  }

  Serial.println("LoRa Receiver");
  LoRa.setPins(ss, rst, dio0);
  if (!LoRa.begin(433E6)) {
    Serial.println();
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.println("Starting LoRa failed!");
    display.display();
    while (1);
  }
  LoRa.setSyncWord(0xA5);
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("ECG Monitor");
  display.setCursor(0, 20);
  display.println("LoRa Initializing Ok");
  display.display();
  delay(2000);
  display.clearDisplay();
  display.display();
}

void loop() {
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    while (LoRa.available()) {
      String data = LoRa.readString();
      int ecgValue = data.toInt();
      Serial.println(ecgValue);
      
      // Draw the point on the screen
      drawPoint(ecgValue);
    }
  }
}

// Function to draw the point on the screen
void drawPoint(int value) {
  static int lastX = 0;
  static int lastY = SCREEN_HEIGHT / 2; // Start from the middle of the screen
  int newY = map(value, 0, 4096, SCREEN_HEIGHT, 0); // Convert the value to the screen range

  // Draw the point
  display.drawLine(lastX, lastY, lastX + 1, newY, SSD1306_WHITE);
  lastX++;
  if (lastX >= SCREEN_WIDTH) {
    lastX = 0;
    display.clearDisplay();
  }
  lastY = newY;

  // Update the screen
  display.display();
}
