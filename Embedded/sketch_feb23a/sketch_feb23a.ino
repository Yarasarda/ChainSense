#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// --- AYARLAR (UUID'ler senin Android tarafıyla eşleşmeli) ---
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
const int MPU_ADDR = 0x68;

// --- DEĞİŞKENLER ---
BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

float angleY = 0;       // Öne eğilme açısı (Pitch)
float lastSentAngle = 0;
unsigned long lastTime = 0;
unsigned long lastBleTime = 0;

// --- FİLTRE VE EŞİK PARAMETRELERİ ---
const float alpha = 0.96;      // %96 Gyro, %4 Accel (Düşük geçiren filtre dengesi)
const float threshold = 0.30;  // 0.3 dereceden küçük oynamaları gönderme (Gürültü engelleme)

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { deviceConnected = true; Serial.println(">> [OK] Telefon Baglandi!"); }
    void onDisconnect(BLEServer* pServer) { deviceConnected = false; Serial.println(">> [UYARI] Baglanti Koptu!"); }
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
  Serial.println("[+] ChainSense Hazir! Pitch (Öne Eğilme) Modu Aktif.");
}

void loop() {
  unsigned long currentTime = micros();
  float dt = (currentTime - lastTime) / 1000000.0;

  // 1. ÖRNEKLEME HIZI: 10ms (100Hz) stabilite için idealdir.
  if (dt < 0.01) return; 
  lastTime = currentTime;

  // 2. I2C VERİ OKUMA: Tüm 14 byte'ı tek seferde çekiyoruz
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x3B); 
  if (Wire.endTransmission(false) != 0) return; 
  
  Wire.requestFrom(MPU_ADDR, 14, true);
  if (Wire.available() < 14) return;

  int16_t rawAcX = Wire.read()<<8 | Wire.read();
  int16_t rawAcY = Wire.read()<<8 | Wire.read(); // Y ivmesini okuyoruz ama Pitch için X ve Z daha kritik
  int16_t rawAcZ = Wire.read()<<8 | Wire.read();
  Wire.read(); Wire.read(); // Sıcaklık verisini çöpe at
  int16_t rawGyX = Wire.read()<<8 | Wire.read(); 
  int16_t rawGyY = Wire.read()<<8 | Wire.read(); // Kamburluk takibi için ana dönüş eksenimiz bu kanka
  int16_t rawGyZ = Wire.read()<<8 | Wire.read();

  // 3. PITCH MATEMATİĞİ VE TAMAMLAYICI FİLTRE
  // Formül: atan2(Ax, Az) bize Y ekseni etrafındaki eğilmeyi verir.
  float ax = rawAcX / 16384.0;
  float az = rawAcZ / 16384.0;
  float gyroRateY = rawGyY / 131.0; 

  float accAngleY = atan2(ax, az) * 180.0 / PI;

  // Complementary Filter: Sarsıntıları temizler, jiroskop kaymasını (drift) engeller.
  angleY = alpha * (angleY + gyroRateY * dt) + (1.0 - alpha) * accAngleY;

  // 4. SMART BLE GÖNDERİMİ
  if (deviceConnected) {
    float diff = abs(angleY - lastSentAngle);
    unsigned long nowBle = millis();

    // Sadece anlamlı bir değişim varsa veya 200ms geçtiyse (keep-alive) veri gönder
    if (diff >= threshold || (nowBle - lastBleTime > 200)) {
      char txString[8]; 
      // Negatif değerlerle uğraşmamak için abs() aldık, Android'de yönü biz belirleriz.
      dtostrf(abs(angleY), 4, 1, txString); 
      
      pCharacteristic->setValue(txString);
      pCharacteristic->notify(); 
      
      lastSentAngle = angleY; 
      lastBleTime = nowBle;
      
      Serial.print("Gonderilen Pitch Acisi: ");
      Serial.println(txString);
    }
  }

  // 5. OTOMATİK RE-ADVERTISE (Bağlantı koptuğunda yayına geri dön)
  if (!deviceConnected && oldDeviceConnected) {
    delay(10); 
    pServer->startAdvertising();
    oldDeviceConnected = deviceConnected;
    Serial.println(">> Yeniden yayin baslatildi...");
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }
  
  delay(2); // BLE Stack'in nefes alması için minik bir boşluk
}