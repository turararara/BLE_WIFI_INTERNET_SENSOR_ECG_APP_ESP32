#include <SPI.h>
#include <LoRa.h>

int ecgValue = 0;
#define ss 5
#define rst 14
#define dio0 2
void setup() {
  Serial.begin(115200);
  while (!Serial);

  Serial.println("LoRa Sender");
  LoRa.setPins(ss, rst, dio0);
  if (!LoRa.begin(433E6)) {
    Serial.println("Starting LoRa failed!");
    while (1);
  }
  LoRa.setSyncWord(0xA5);
  Serial.println("LaRa Initializing Ok");
}

void loop() {
  ecgValue=analogRead(35);
  String ecgValueString=(String)ecgValue;
  //Serial.println(ecgValueString);

  // send packet
  LoRa.beginPacket();
  LoRa.print(ecgValueString);
  LoRa.endPacket();
  delay(1);
  
}
