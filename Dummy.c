void setup() {
  // initialize serial communication at 9600 bits per second:
  Serial.begin(9600);
}
void loop() {
  char incomingByte;
   // If there is a data stored in the serial receive buffer, read it and print it to the serial port as human-readable ASCII text.
  if(Serial.available()){  
    incomingByte = Serial.read();
    Serial.print(incomingByte);  
  }
}