[![Maven Central](https://img.shields.io/maven-central/v/org.sputnikdev/bluetooth-manager.svg)](https://mvnrepository.com/artifact/org.sputnikdev/bluetooth-manager)
[![Build Status](https://travis-ci.org/sputnikdev/bluetooth-manager.svg?branch=master)](https://travis-ci.org/sputnikdev/bluetooth-manager)
[![Coverage Status](https://coveralls.io/repos/github/sputnikdev/bluetooth-manager/badge.svg?branch=master)](https://coveralls.io/github/sputnikdev/bluetooth-manager?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/5afbd725e7b24215a350b6d9921a3684)](https://www.codacy.com/app/vkolotov/bluetooth-manager?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=sputnikdev/bluetooth-manager&amp;utm_campaign=Badge_Grade)
[![Join the chat at https://gitter.im/sputnikdev/bluetooth-manager](https://badges.gitter.im/sputnikdev/bluetooth-manager.svg)](https://gitter.im/sputnikdev/bluetooth-manager?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
# bluetooth-manager

[![Join the chat at https://gitter.im/sputnikdev/bluetooth-manager](https://badges.gitter.im/sputnikdev/bluetooth-manager.svg)](https://gitter.im/sputnikdev/bluetooth-manager?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
A library/framework for managing bluetooth adapters, bluetooth devices, GATT services and characteristics

The Bluetooth Manager is a set of java APIs which is designed to streamline all the hard work of dealing with unstable 
by its nature Bluetooth protocol. 

## KPIs

The following KPIs were kept in mind while designing and implementing the Bluetooth Manager:

1. Flexibility in using different transports, e.g. serial port, dbus or any other (like tinyb).
2. Extensibility in adding new supported devices, e.g. different sensors and other hardware.
3. Robustness. Due to the nature of the Bluetooth protocol the biggest challenge is making the API stable enough 
so that end-users could use it.
4. Comprehensive support for Bluetooth GATT specifications. This is a powerful feature which would allow users:
    1. add any device which conforms GATT specification without developing any custom code
    2. add custom made devices by only specifying a path to a custom defined GATT specification for a device
 
## Implementation overview 
 
The following diagram outlines some details of the Bluetooth Manager:
![Bluetooth Manager diagram](bluetooth-manager.png?raw=true "Bluetooth Manager diagram") 
 
### Governors API

The central part of the BM architecture is "Governors" (examples and more info 
[BluetoothGovernor](https://github.com/sputnikdev/bluetooth-manager/blob/master/src/main/java/org/sputnikdev/bluetooth/manager/BluetoothGovernor.java),  
[AdapterGovernor](https://github.com/sputnikdev/bluetooth-manager/blob/master/src/main/java/org/sputnikdev/bluetooth/manager/AdapterGovernor.java), 
[DeviceGovernor](https://github.com/sputnikdev/bluetooth-manager/blob/master/src/main/java/org/sputnikdev/bluetooth/manager/DeviceGovernor.java) and 
[BluetoothManager](https://github.com/sputnikdev/bluetooth-manager/blob/master/src/main/java/org/sputnikdev/bluetooth/manager/BluetoothManager.java)). 
These are the components which define lifecycle of BT objects and contain logic for error recovery. They are similar to the transport APIs (see below), 
but yet different because they are "active" components, i.e. they implement some logic for each of BT objects (adapter, device, characteristic) that make 
the system more robust and enable the system to recover from unexpected situations such as disconnections and power outages.

Apart from making the system stable, the Governors are designed in a such way that they can be used externally, 
in other words it is another abstraction layer which hides some specifics/difficulties of the BT protocol behind user-friendly APIs.
 
### Transport API

A specially designed abstraction layer (transport) is used to bring support 
for various bluetooth adapters/dongles, operation systems and hardware architecture types.

The following diagram outlines some details of the Bluetooth Manager Transport abstraction layer:
![Transport diagram](bm-transport-abstraction-layer.png?raw=true "Bluetooth Manager Transport abstraction layer")

There are two implementations of the BT Transport currently:
 - [TinyB Transport](https://github.com/sputnikdev/bluetooth-manager-tinyb).
    The TinyB transport brings support for:
     * Conventional USB bluetooth dongles. 
     * Linux based operation systems.
     * A wide range of hardware architectures (including some ARM based devices, e.g. Raspberry PI etc).
 - WIP: [Bluegiga Transport](https://github.com/sputnikdev/bluetooth-manager-bluegiga).
    The Bluegiga transport brings support for:
     * Bluegiga (BLE112) USB bluetooth dongles. 
     * Linux, Windows and OSX based operation systems.
     * A wide range of hardware architectures (including some ARM based devices, e.g. Raspberry PI etc).

---
## Contribution

You are welcome to contribute to the project, the project environment is designed to make it easy by using:
* Travis CI to release artifacts directly to the Maven Central repository.
* Code style rules to support clarity and supportability. The results can be seen in the Codacy. 
* Code coverage reports in the Coveralls to maintain sustainability. 100% of code coverage with unittests is the target.

The build process is streamlined by using standard maven tools. 

To build the project with maven:
```bash
mvn clean install
```

To cut a new release and upload it to the Maven Central Repository:
```bash
mvn release:prepare -B
mvn release:perform
```
Travis CI process will take care of everything, you will find a new artifact in the Maven Central repository when the release process finishes successfully.