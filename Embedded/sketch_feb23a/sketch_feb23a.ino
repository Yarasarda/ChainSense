#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
const int MPU_ADDR = 0x68;

const int batteryPin = A0; 

BLEServer* pServer = NULL;
BLECharacteristic* pPitchCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

float angleY = 0;       
float lastSentAngle = 0;
unsigned long lastTime = 0;
unsigned long lastBleTime = 0;
const float alpha = 0.96;      
const float threshold = 0.30;  

float filteredVoltage = 0.0;
const float batAlpha = 0.1; 
bool firstBatReading = true;
unsigned long lastBatTimer = 0;

uint8_t globalBatteryPct = 0; 

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { deviceConnected = true; Serial.println(">> [OK] Telefon Baglandi!"); }
    void onDisconnect(BLEServer* pServer) { deviceConnected = false; Serial.println(">> [UYARI] Baglanti Koptu!"); }
};

void setup(void) {
  Serial.begin(115200);
  Wire.begin(); 
  delay(100);
  pinMode(batteryPin, INPUT);

  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x6B); 
  Wire.write(0);    
  Wire.endTransmission(true);

  BLEDevice::init("ChainSense");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  
  BLEService *pPitchService = pServer->createService(SERVICE_UUID);
  pPitchCharacteristic = pPitchService->createCharacteristic(
                          CHARACTERISTIC_UUID,
                          BLECharacteristic::PROPERTY_NOTIFY
                        );
  pPitchCharacteristic->addDescriptor(new BLE2902());
  pPitchService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  BLEDevice::startAdvertising();

  lastTime = micros();
  Serial.println("[+] ChainSense Tek Kanal (Multiplex) Hazir!");
}

void loop() {
  unsigned long currentTime = micros();
  unsigned long currentMillis = millis();
  float dt = (currentTime - lastTime) / 1000000.0;

  // --- 1 BATARYA OKUMA (Sessizce değişkene kaydet, BLE'den yollama!) ---
  if (currentMillis - lastBatTimer > 2000) {
    lastBatTimer = currentMillis;
    
    uint32_t rawVbatt = 0;
    for(int i = 0; i < 16; i++) rawVbatt += analogReadMilliVolts(batteryPin);
    float currentV = (2.0 * rawVbatt / 16.0) / 1000.0;

    if(firstBatReading) {
      filteredVoltage = currentV;
      firstBatReading = false;
    } else {
      filteredVoltage = (batAlpha * currentV) + ((1.0 - batAlpha) * filteredVoltage);
    }

    float pctFloat = ((filteredVoltage - 3.2) / (4.2 - 3.2)) * 100.0;
    if (pctFloat > 100) pctFloat = 100;
    if (pctFloat < 0) pctFloat = 0;
    
    globalBatteryPct = (uint8_t)pctFloat; // Sadece hafızada tut
  }

  // --- 2 MPU6050 VE PAYLOAD GÖNDERİMİ ---
  if (dt >= 0.01) { 
    lastTime = currentTime;

    Wire.beginTransmission(MPU_ADDR);
    Wire.write(0x3B); 
    if (Wire.endTransmission(false) == 0 && Wire.requestFrom(MPU_ADDR, 14, true) == 14) {
      
      int16_t rawAcX = Wire.read()<<8 | Wire.read();
      Wire.read(); Wire.read(); 
      int16_t rawAcZ = Wire.read()<<8 | Wire.read();
      Wire.read(); Wire.read(); 
      Wire.read(); Wire.read(); 
      int16_t rawGyY = Wire.read()<<8 | Wire.read(); 
      Wire.read(); Wire.read(); 

      float ax = rawAcX / 16384.0;
      float az = rawAcZ / 16384.0;
      float gyroRateY = rawGyY / 131.0; 

      float accAngleY = atan2(ax, az) * 180.0 / PI;
      angleY = alpha * (angleY + gyroRateY * dt) + (1.0 - alpha) * accAngleY;

      if (deviceConnected) {
        float diff = abs(angleY - lastSentAngle);
        if (diff >= threshold || (currentMillis - lastBleTime > 200)) {
          
          char pitchStr[8]; 
          dtostrf(abs(angleY), 4, 1, pitchStr); 
          
          char payload[20];
          sprintf(payload, "%s|%d", pitchStr, globalBatteryPct);
          
          pPitchCharacteristic->setValue(payload);
          pPitchCharacteristic->notify(); 
          
          lastSentAngle = angleY; 
          lastBleTime = currentMillis;
        }
      }
    }
  }

  if (!deviceConnected && oldDeviceConnected) {
    delay(500); 
    pServer->startAdvertising();
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }
}