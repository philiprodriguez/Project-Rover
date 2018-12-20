/*
  Philip Rodriguez
  December 7, 2018

  This is a simple software for the Teensy that is the basic controller for the robot. This software's job is to
  take commands in over the Bluetooth Serial module on the Serial3 interface, and then "apply" those commands to
  the physical hardware of the robot, whether it be the motors, lights, servos, etc... This should be a wait-free
  and very simple code. Process incoming commands sequentially, without any delaying if at all possible.
 */

int pinLED = 13;
int pinLeftForward = 23;
int pinLeftBackward = 22;
int pinRightForward = 21;
int pinRightBackward = 20;

void setup() {                
  // initialize the digital pin as an output.
  pinMode(pinLeftForward, OUTPUT); // Left forward
  pinMode(pinLeftBackward, OUTPUT); // Left backward
  pinMode(pinRightForward, OUTPUT); // Right forward
  pinMode(pinRightBackward, OUTPUT); // Right backward


  // Set ALL motor pins (which are sharing the same timer) from their default of a loud 488Mhz to the silent 60Khz
  analogWriteFrequency(pinLeftForward, 30000);
  
  pinMode(13, OUTPUT);
  Serial.begin(9600);
  Serial3.begin(9600);
  Serial3.setTimeout(10000);
  delay(1000);
}

#define START_SEQUENCE_LENGTH 8
const int START_SEQUENCE[START_SEQUENCE_LENGTH] = {'a', '8', 'f', 'e', 'J', '2', '9', 'p'};

int nextSerial3Byte() {
  int readVal = -1;
  while ((readVal = Serial3.read()) == -1) {
    // Do nothing!
  }
  return readVal;
}

void loop() {
  // Look for start sequence
  Serial.println("Waiting for start sequence...");
  for (int i = 0; i < START_SEQUENCE_LENGTH; i++) {
    if (nextSerial3Byte() != START_SEQUENCE[i]) {
      // Failure to read start sequence! Retry!
      return;
    }
  }
  Serial.println("Got start sequence!");
  int opcode = nextSerial3Byte();
  Serial.println("Opcode:");
  Serial.println(opcode);

  if (opcode == 'm') {
    // Motor set
    Serial.println("Opcode was for motor set...");
    int leftForward = nextSerial3Byte();
    int leftBackward = nextSerial3Byte();
    int rightForward = nextSerial3Byte();
    int rightBackward = nextSerial3Byte();
    analogWrite(pinLeftForward, leftForward);
    analogWrite(pinLeftBackward, leftBackward);
    analogWrite(pinRightForward, rightForward);
    analogWrite(pinRightBackward, rightBackward);
    Serial.println("Motors set:");
    Serial.println(leftForward);
    Serial.println(leftBackward);
    Serial.println(rightForward);
    Serial.println(rightBackward);
  } else if (opcode == 'l') {
    // Motor set
    Serial.println("Opcode was for led set...");
    int val = nextSerial3Byte();
    if (val == 'h') {
      digitalWrite(pinLED, HIGH);
    } else if (val == 'l') {
      digitalWrite(pinLED, LOW);
    }
  } else {
    Serial.println("Illegal opcode!");
    return;
  }
}
