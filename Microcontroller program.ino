#include <WS2812FX.h>
#include <ESP8266WiFi.h>
#include <EEPROM.h>
#include <ESP8266mDNS.h>
#include <NTPClient.h>
#include <WiFiUdp.h>


#define LED_COUNT 8 //Ilosc diod
#define LED_PIN D5 //Pin do sterowania

const int buttonPin = D0;
int buttonState = 0;

WS2812FX ws2812fx = WS2812FX(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);

WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "pl.pool.ntp.org", 7200);

WiFiServer server(80);
MDNSResponder mdns;
#define MAX_STRING_LENGTH 25
struct {
  char mySSID[MAX_STRING_LENGTH] = "";
  char myPW[MAX_STRING_LENGTH] = "";
  int paired;
  int deviceID;
  int brightnessMEM;
  byte red;
  byte green;
  byte blue;
  int hourON;
  int minuteON;
  int hourOFF;
  int minuteOFF;
} settings;

unsigned int addr = 0;
String data;
String option;

void setup() {
  Serial.begin(9600);
  pinMode(buttonPin, INPUT_PULLUP);
  EEPROM.begin(sizeof(settings));
  EEPROM.get(addr, settings);
  Serial.println(settings.mySSID);
  Serial.println(settings.myPW);
  Serial.println(settings.deviceID);
  Serial.println(settings.hourON);
  Serial.println(settings.minuteON);
  Serial.println(settings.hourOFF);
  Serial.println(settings.minuteOFF);

  timeClient.begin();

  String devID = String(settings.deviceID);
  ws2812fx.init();
  ws2812fx.setBrightness(settings.brightnessMEM);
  ws2812fx.setSpeed(255);

  if (settings.paired == 1)
  {
    ws2812fx.setMode(FX_MODE_STATIC);
    ws2812fx.setColor(settings.red, settings.green, settings.blue);
    ws2812fx.start();
    ws2812fx.service();
    WiFi.begin(settings.mySSID, settings.myPW);
    while (WiFi.status() != WL_CONNECTED) { // Wait for the Wi-Fi to connect
      buttonState = digitalRead(buttonPin);
      if (buttonState == LOW)
        backToDefault();
      delay(500);
    }
    Serial.println("Connection established!");
    Serial.println(WiFi.localIP());
    if (!MDNS.begin(devID)) {             // Start the mDNS responder for esp8266.local
      Serial.println("Error setting up MDNS responder!");
    }
    MDNS.addService("http", "tcp", 80);
    Serial.println("mDNS responder started");
    server.begin();
    ws2812fx.setMode(FX_MODE_STATIC);
    ws2812fx.setBrightness(settings.brightnessMEM);
    ws2812fx.setColor(settings.red, settings.green, settings.blue);
  }
  else
  {
    WiFi.softAP("HMsmartLight");
    ws2812fx.setMode(FX_MODE_BLINK);
    ws2812fx.setColor(GREEN);
    ws2812fx.start();
    ws2812fx.service();
    server.begin();
  }
}

void loop() {

  buttonState = digitalRead(buttonPin);

  if (option == "D" || buttonState == LOW)
    backToDefault();

  onTime();

  ws2812fx.service();

  if (settings.paired == 1)
    MDNS.update();

  WiFiClient client = server.available();
  if (!client) {
    return;
  }

  data = client.readStringUntil('\n');
  option = data.substring(6, 7);
  Serial.println(option);

  if (option == "P") //Parowanie
    pairing();

  if (option == "C") //Zmiana koloru
    changeColor();

  if (option == "B") //Zmiana jasnosci
    changeBrightness();

  if (option == "S") //Powrot do stałego świecenia
    backToStatic();

  if (option == "R") //Rainbow
    ws2812fx.setMode(FX_MODE_RAINBOW);

  if (option == "X") //Crazy
    ws2812fx.setMode(FX_MODE_TWINKLE_RANDOM);

  if (option == "T") //Ustawianie harmonogramu
    scheduleOn();

  if (option == "Y") //Wyłączanie harmonogramu
    scheduleOff();

  Serial.println(data);
  client.flush();
}

void pairing()
{
  char Buff[60];
  char BuffSSID[25];
  char BuffPW[25];
  char *token;
  String arr[3];
  int i = 0;
  data.toCharArray(Buff, 60);
  token = strtok (Buff, "-");
  while (token != NULL)
  {
    token = strtok (NULL, "-");
    arr[i] = token;
    i++;
  }
  arr[1].toCharArray(BuffSSID, 25);
  arr[2].toCharArray(BuffPW, 25);
  strncpy(settings.mySSID, BuffSSID, MAX_STRING_LENGTH);
  strncpy(settings.myPW, BuffPW, MAX_STRING_LENGTH);
  settings.deviceID = arr[0].toInt();
  settings.paired = 1;
  addr = 0;
  EEPROM.put(addr, settings);
  EEPROM.commit();
  WiFi.softAPdisconnect (true);
  ESP.restart();
}

void changeColor()
{
  String color = data.substring(7, 13);
  char charbuf[8];
  color.toCharArray(charbuf, 8);
  long int rgb = strtol(charbuf, 0, 16);
  byte r = (byte)(rgb >> 16);
  byte g = (byte)(rgb >> 8);
  byte b = (byte)(rgb);
  ws2812fx.setColor(r, g, b);
  settings.red = r;
  settings.green = g;
  settings.blue = b;
  addr = 0;
  EEPROM.put(addr, settings);
  EEPROM.commit();
}

void changeBrightness()
{
  String BrightString = data.substring(7, 10);
  int Brightness = BrightString.toInt();
  ws2812fx.setBrightness(Brightness);
  settings.brightnessMEM = Brightness;
  addr = 0;
  EEPROM.put(addr, settings);
  EEPROM.commit();
}

void backToStatic()
{
  ws2812fx.setMode(FX_MODE_STATIC);
  String color = data.substring(7, 13);
  char charbuf[8];
  color.toCharArray(charbuf, 8);
  long int rgb = strtol(charbuf, 0, 16);
  byte r = (byte)(rgb >> 16);
  byte g = (byte)(rgb >> 8);
  byte b = (byte)(rgb);
  ws2812fx.setColor(r, g, b);
  settings.red = r;
  settings.green = g;
  settings.blue = b;
  addr = 0;
  EEPROM.put(addr, settings);
  EEPROM.commit();
}

void backToDefault()
{
  Serial.println("RESTART");
  scheduleOff();
  settings.deviceID = 0;
  strncpy(settings.mySSID, " ", MAX_STRING_LENGTH);
  strncpy(settings.myPW, " ", MAX_STRING_LENGTH);
  settings.paired = 0;
  settings.brightnessMEM = 127;
  settings.red = 255;
  settings.green = 255;
  settings.blue = 255;
  addr = 0;
  EEPROM.put(addr, settings);
  EEPROM.commit();
  ESP.restart();
}

void scheduleOn()
{
  int HourOn = data.substring(7, 9).toInt();
  int MinuteOn = data.substring(9, 11).toInt();
  int HourOff = data.substring(11, 13).toInt();
  int MinuteOff = data.substring(13, 15).toInt();
  settings.hourON = HourOn;
  settings.hourOFF = HourOff;
  settings.minuteON = MinuteOn;
  settings.minuteOFF = MinuteOff;
  addr = 0;
  EEPROM.put(addr, settings);
  EEPROM.commit();
}

void scheduleOff()
{
  settings.hourON = 70;
  settings.hourOFF = 70;
  settings.minuteON = 70;
  settings.minuteOFF = 70;
  addr = 0;
  EEPROM.put(addr, settings);
  EEPROM.commit();
}

void onTime()
{
  timeClient.update();
  //Serial.println(timeClient.getFormattedTime());

  if (timeClient.getHours() == settings.hourOFF && timeClient.getMinutes() == settings.minuteOFF)
    ws2812fx.setBrightness(0);

  if (timeClient.getHours() == settings.hourON && timeClient.getMinutes() == settings.minuteON)
    ws2812fx.setBrightness(settings.brightnessMEM);
}
