#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// --- AYARLAR ---
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
const int MPU_ADDR = 0x68;

// --- DEĞİŞKENLER ---
BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

float angleX = 0; 
float lastSentAngle = 0;
unsigned long lastTime = 0;
unsigned long lastBleTime = 0;

// --- FİLTRE VE EŞİK PARAMETRELERİ ---
const float alpha = 0.96;      // %96 Gyro, %4 Accel (Dengeli pürüzsüzlük)
const float threshold = 0.25;  // 0.25 dereceden küçük oynamaları gönderme

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { deviceConnected = true; Serial.println(">> [OK] Baglandi!"); }
    void onDisconnect(BLEServer* pServer) { deviceConnected = false; Serial.println(">> [UYARI] Koptu!"); }
};

void setup(void) {
  Serial.begin(115200);
  Wire.begin(); 
  delay(100);

  // MPU6050 Manuel Uyandırma
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x6B); 
  Wire.write(0);    
  Wire.endTransmission(true);

  // BLE Kurulum
  BLEDevice::init("ChainSense");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  
  BLEService *pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();
  BLEDevice::startAdvertising();

  lastTime = micros();
  Serial.println("[+] Sistem Hazir, Titreşim Filtresi Aktif!");
}

void loop() {
  unsigned long currentTime = micros();
  float dt = (currentTime - lastTime) / 1000000.0;

  // 1. CPU REFAHI: Saniyede 100 okumadan fazlasına gerek yok (10ms)
  if (dt < 0.01) return; 
  lastTime = currentTime;

  // 2. I2C HATTI GÜVENLİĞİ: Sadece lazım olanı iste ve hattı boşalt
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x3B); 
  if (Wire.endTransmission(false) != 0) return; 
  
  Wire.requestFrom(MPU_ADDR, 14, true); // 14 byte'ın tamamını çek
  if (Wire.available() < 14) return;

  int16_t rawAcX = Wire.read()<<8 | Wire.read();
  int16_t rawAcY = Wire.read()<<8 | Wire.read();
  int16_t rawAcZ = Wire.read()<<8 | Wire.read();
  Wire.read(); Wire.read(); // Sıcaklığı atla
  int16_t rawGyX = Wire.read()<<8 | Wire.read();
  Wire.read(); Wire.read(); Wire.read(); Wire.read(); // GyY ve GyZ'yi tüket, hattı temizle!

  // 3. MATEMATİK VE FİLTRE
  float ay = rawAcY / 16384.0;
  float az = rawAcZ / 16384.0;
  float gyroRateX = rawGyX / 131.0; 
  float accAngleX = atan2(ay, az) * 180.0 / PI;
  angleX = alpha * (angleX + gyroRateX * dt) + (1.0 - alpha) * accAngleX;

  // 4. SMART BLE: Sadece anlamlı değişim varsa veya 200ms geçtiyse (Heartbeat)
  if (deviceConnected) {
    float diff = abs(angleX - lastSentAngle);
    unsigned long nowBle = millis();

    if (diff >= threshold || (nowBle - lastBleTime > 200)) {
      char txString[8]; 
      dtostrf(abs(angleX), 4, 1, txString); // Mutlak değer ve tek hane hassasiyet
      
      pCharacteristic->setValue(txString);
      pCharacteristic->notify(); 
      
      lastSentAngle = angleX; 
      lastBleTime = nowBle;
    }
  }

  // 5. RE-ADVERTISE (Bağlantı koptuğunda otomatik yayın)
  if (!deviceConnected && oldDeviceConnected) {
    delay(10); 
    pServer->startAdvertising();
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }
  delay(2); // BLE Stack'e zaman tanı
}