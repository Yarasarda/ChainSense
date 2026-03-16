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

// MPU6050 I2C Adresi
const int MPU_ADDR = 0x68;

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

  // 1. I2C Hattını Başlat
  Wire.begin(SDA, SCL); 
  delay(100);

  // 2. MPU6050'yi Manuel Uyandır (Kütüphanesiz Bare Metal Metodu)
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x6B); // PWR_MGMT_1 register'ı
  Wire.write(0);    // Çipi uyandır (0 yazarak uyku modunu kapatıyoruz)
  byte error = Wire.endTransmission(true);

  if (error != 0) {
    Serial.println("[-] MPU6050 Uyanmadi! Adres veya kablo hatasi.");
    while(1) yield(); // Donanım kopuksa burada kalır
  }
  Serial.println("[+] MPU6050 Bare Metal Modunda Ayaga Kalkti!");
  
  // 3. BLE Başlatma
  BLEDevice::init("ChainSense"); 
  
  String myAddress = BLEDevice::getAddress().toString().c_str();
  Serial.println("******************************************");
  Serial.print("[+] ESP32 BLE MAC ADRESI: ");
  Serial.println(myAddress); 
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
  // MPU6050'den İvme Verilerini Manuel Oku
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x3B); // İvme verilerinin başladığı adres
  Wire.endTransmission(false);
  Wire.requestFrom(MPU_ADDR, 6, true); // 6 byte (X, Y, Z için ikişer byte) iste

  // Verileri birleştir (Yüksek byte << 8 | Düşük byte)
  int16_t rawAcX = Wire.read() << 8 | Wire.read();
  int16_t rawAcY = Wire.read() << 8 | Wire.read();
  int16_t rawAcZ = Wire.read() << 8 | Wire.read();

  // Hassasiyet ayarı (MPU6050 varsayılan +-2g hassasiyetindedir -> 16384 LSB/g)
  float ax = rawAcX / 16384.0;
  float ay = rawAcY / 16384.0;
  float az = rawAcZ / 16384.0;

  // Kalman Filtresi ile gürültüyü temizle
  float fAx = kalmanX.updateEstimate(ax);
  float fAy = kalmanY.updateEstimate(ay);
  float fAz = kalmanZ.updateEstimate(az - OFFSET_Z);

  // Eğim açısını hesapla (Pitch)
  float angleX = atan2(fAy, sqrt(fAx * fAx + fAz * fAz)) * 180.0 / PI;

  // Sadece cihaz bağlıyken veri gönder
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