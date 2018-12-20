# Project Rover
## Description
  Project Rover is abstractly a remotely-controllable robot with a camera for
FPV driving. It has the ability to be docked or parked into a charging spot so
that is can be driven remotely indefinitely (or, well, for the lifespan of the
physical components on the robot). The camera can be tilted up and down. It can
also be panned by simply turning the robot.

## Implementation Description
  Project Rover is developed using primarily the Android platform. The robot
contains a dedicated Android device. This Android device supplies the robot with
a camera to use, a computer on which to run the server application, a flash LED
to be used as a headlight, a Bluetooth connection, and a Wi-Fi connection. The
client sends locomotion commands to the server over a standard Java Socket. The
server process the commands and sends them off via Bluetooth to a Teensy 3.x
device, which has an attached L298N motor controller to power two drive motors.
The robot (server Android device, Teensy 3.x, motors, etc) is powered by a 12V
SLA AGM battery of 144 watt-hours, which should supply at a minimum 6 hours of
continuous, full-speed/full-power run time. To charge, the robot simply drives
into its parking spot, which has two power rails that connect with plates on the
underbelly of the robot. The server streams image data through a standard Java
Socket to the client Android application. This image data is simply a stream of
JPEG frames. Typical connection requirements are 300 KB/s. Typical image latency
is about 150-500 ms depending on if a LAN connection is used vs a remote
connection or even a cellular 4G connection.


Copyright Philip Rodriguez
