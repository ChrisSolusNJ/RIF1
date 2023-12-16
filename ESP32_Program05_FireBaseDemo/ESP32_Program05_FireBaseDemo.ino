//Librerias para el funcionamiento del sistema
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"
#include <DHT.h>
#include <LiquidCrystal_I2C.h>
#include <Wire.h>   

//Definicion de credenciales para conectar a la base de datos y red WiFi
#define WIFI_SSID "chrisnj"
#define WIFI_PASSWORD "12345678"
#define API_KEY "AIzaSyClANVZPH1pJLPtuYhCe8YhXSnTYmH6VzQ"
#define DATABASE_URL "https://esp32-sra-iot-default-rtdb.firebaseio.com/" 

//Inicializar pantalla LCD
LiquidCrystal_I2C lcd(0x27,16,2);


//Definir pines a utilizar
#define sensorHumedad 35
#define bombPin 16
#define pinLiquido 17
#define pinAlarma 18
DHT dht (4,DHT11);

//Valores a utilizar
int valorHumedad;
int porcentajehumedad;
int valor = 0;
int tiempo = 0;
float Temperatura;
unsigned long sendDataPrevMillis = 0;
bool signupOK = false;
bool bombStatus;
bool bombStatus1;

//Objetos de acceso y credenciales de base de datos
FirebaseData fbdo, fbdo_s1, fbdo_s2, fbdo_s3;
FirebaseAuth auth;
FirebaseConfig config;

void setup() {
  //Inicialización de variables
  Serial.begin(9600);
  pinMode(pinLiquido, INPUT);
  pinMode(pinAlarma, OUTPUT);
  dht.begin();
  delay(3000);
  Wire.begin();
  lcd.init();
  lcd.clear();         
  lcd.backlight(); 
  pinMode(bombPin, OUTPUT);
  
  //Conexión a WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectado a Wi-Fi");
  //Mientras sea distinto de conectado a WiFi, imprimir un "."
  while(WiFi.status() != WL_CONNECTED){
    Serial.print(".");
    delay(300);
  }
  //Sale del bucle y emite el mensaje de conectado a WiFi con IP
  Serial.println();
  Serial.print("Conectado con IP: ");
  Serial.println(WiFi.localIP());
  Serial.println();
  
  //Conectarse a FireBase
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  //Pasamos los parametros de conexión
  if(Firebase.signUp(&config, &auth, "", "")){
    Serial.println("SignUp OK"); //Si se conecta, mandar este mensaje
    signupOK = true; 
  }else{
    Serial.printf("%s\n", config.signer.signupError.message.c_str());  //Error de coenxión a Firebase
  }

  config.token_status_callback = tokenStatusCallback;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  //Stream para acceder a datos de rutas en Firebase para utilizar
  if(!Firebase.RTDB.beginStream(&fbdo_s1, "/BOMBA/digital"))
    Serial.printf("stream 1 begin error, $s\n\n", fbdo_s1.errorReason().c_str());
     
}


void loop() {
  
  if(Firebase.ready() && signupOK && (millis() - sendDataPrevMillis > 3000 || sendDataPrevMillis == 0)){
      
      Temperatura = dht.readTemperature();
      sendDataPrevMillis = millis();
      Serial.print("Temp: "); 
      Serial.print(Temperatura);
      Serial.println(" C");
    
      valorHumedad = analogRead(sensorHumedad);
      humedad(valorHumedad);
      Serial.print("Humedad: ");
      Serial.print(porcentajehumedad);
      Serial.println("%");
      delay(1000);

      valor = digitalRead(pinLiquido); 
      Serial.println(valor);

      //Para activar la alarma se pregunta lo siguiente
      if(valor == 0){ // Si el sensor esta en el estado 0 se prende le led del pin 13 
        digitalWrite(pinAlarma, HIGH);
      }else{ //De lo contrario estará apagado el led del pin 13 
        digitalWrite(pinAlarma, LOW);
      }

       // Mostrar en la pantalla LCD
      lcd.clear();
      lcd.setCursor(0, 0);
      lcd.print("Temp: ");
      lcd.print(Temperatura);
      lcd.print(" C");
    
      lcd.setCursor(0, 1);
      lcd.print("Humedad: ");
      lcd.print(porcentajehumedad);
      lcd.print(" %");
      delay(1000);

    //Guardar en RTDB
      if(Firebase.RTDB.setFloat(&fbdo, "Sensor/sensor_data", porcentajehumedad)){
        Serial.println();
        Serial.print(porcentajehumedad);
        Serial.println("Valor del sensor: " + String(porcentajehumedad));
        Serial.print(" - Guardado correctamente en: " + fbdo.dataPath());
        Serial.println(" (" + fbdo.dataType() + " ) ");   
      }else{
        Serial.println("FAILED: " + fbdo.errorReason());
      }

      if(Firebase.RTDB.setFloat(&fbdo, "Sensor/temp", Temperatura)){
      Serial.println();
      Serial.print(Temperatura);
      Serial.println("Valor del sensor: " + String(Temperatura));
      Serial.print(" - Guardado correctamente en: " + fbdo.dataPath());
      Serial.println(" (" + fbdo.dataType() + " ) ");   
      }else{
      Serial.println("FAILED: " + fbdo.errorReason());
      }
      if(Firebase.RTDB.setFloat(&fbdo, "SensorLiquido/lleno", valor)){
        Serial.println();
        Serial.print(valor);
        Serial.println("Valor del sensor liquido: " + String(valor));
        Serial.print(" - Guardado correctamente en: " + fbdo.dataPath());
        Serial.println(" (" + fbdo.dataType() + " ) ");   
      }else{
        Serial.println("FAILED: " + fbdo.errorReason());
      }    
    }
    
  //Leer datos de RTDB onDataChange
  if(Firebase.ready() && signupOK){
    if(!Firebase.RTDB.readStream(&fbdo_s1))
      Serial.printf("Error en lectura Stream, %s\n\n", fbdo_s1.errorReason().c_str());
    if(fbdo_s1.streamAvailable()){
      if(fbdo_s1.dataType() == "boolean"){
        bombStatus = fbdo_s1.boolData();
        Serial.println("Correctamente leído de " + fbdo_s1.dataPath() + ": " + bombStatus + " (" + fbdo_s1.dataType() + ")");
        // Cambiar el estado de bombPin
        digitalWrite(bombPin, bombStatus ? HIGH : LOW);
      }
    }

  }
}

void humedad(int lectura) {
  porcentajehumedad = map(lectura, 4095, 1904, 0, 100);  // Corregir la llamada a la función map
}
