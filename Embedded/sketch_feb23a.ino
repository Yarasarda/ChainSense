#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <SimpleKalmanFilter.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// --- BLE AYARLARI ---
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

Adafruit_MPU6050 mpu;
SimpleKalmanFilter kalmanX(2, 2, 0.01);
SimpleKalmanFilter kalmanY(2, 2, 0.01);
SimpleKalmanFilter kalmanZ(2, 2, 0.01);

const float OFFSET_Z = 1.37; 
unsigned long lastBleTime = 0;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println(">> [OK] Telefon Baglandi!");
    };
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println(">> [UYARI] Telefon Koptu!");
    }
};

void setup(void) {
  Serial.begin(115200);
  while (!Serial) delay(10); // Serial hazır olana kadar bekle

  // 1. MPU6050 Başlatma
  if (!mpu.begin()) {
    Serial.println("[-] MPU6050 Bulunamadi!");
    while (1) yield();
  }
  
  // 2. BLE Başlatma (SIRALAMA ÇOK ÖNEMLİ AGA!)
  BLEDevice::init("ChainSense"); // Önce init et
  
  // ŞİMDİ MAC ADRESİNİ OKUYORUZ:
  String myAddress = BLEDevice::getAddress().toString().c_str();
  Serial.println("******************************************");
  Serial.print("[+] ESP32 BLE MAC ADRESI: ");
  Serial.println(myAddress); // İşte Android koduna yazacağın adres bu!
  Serial.println("******************************************");

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE  |
                      BLECharacteristic::PROPERTY_NOTIFY | 
                      BLECharacteristic::PROPERTY_INDICATE
                    );

  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0); 
  BLEDevice::startAdvertising();
  
  Serial.println("[+] Reklam Basladi. Telefonundan Baglanabilirsin.");
}

void loop() {
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  // Kalman Filtresi ile gürültüyü temizle
  float fAx = kalmanX.updateEstimate(a.acceleration.x);
  float fAy = kalmanY.updateEstimate(a.acceleration.y);
  float fAz = kalmanZ.updateEstimate(a.acceleration.z - OFFSET_Z);

  // Eğim açısını hesapla (Pitch)
  float angleX = atan2(fAy, sqrt(fAx * fAx + fAz * fAz)) * 180.0 / PI;

  // Sadece cihaz bağlıyken ve 150ms'de bir veri gönder
  if (deviceConnected && (millis() - lastBleTime > 150)) {
    char txString[8]; 
    dtostrf(angleX, 4, 2, txString); 
    
    pCharacteristic->setValue(txString);
    pCharacteristic->notify(); 
    
    Serial.print("BLE_Gonderilen_Aci: ");
    Serial.println(txString);
    lastBleTime = millis();
  }

  // Bağlantı koparsa reklamı tekrar başlat
  if (!deviceConnected && oldDeviceConnected) {
      delay(500); 
      pServer->startAdvertising(); 
      Serial.println(">> [RE-ADVERTISE] Yeni baglanti bekleniyor...");
      oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
      oldDeviceConnected = deviceConnected;
  }

  delay(10); 
}