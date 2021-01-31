part of tcn_serial;

class SerialPort {
  SerialPort(MethodChannel channel, EventChannel eventChannel, Device device,
      int baudrate) {
    this.device = device;
    this.baudrate = baudrate;
    this._channel = channel;
    this._eventChannel = eventChannel;
    this._deviceConnected = false;

    this._channel.setMethodCallHandler((MethodCall call) {
      print('call.method ${call.method}');
      print('call.arguments ${call.arguments}');
      _dataSerialBS.sink.add(call.arguments);
      return;
    });
  }

  int baudrate;
  Device device;

  MethodChannel _channel;
  BehaviorSubject<String> _dataSerialBS =
      new BehaviorSubject<String>.seeded(null);

  bool _deviceConnected;
  EventChannel _eventChannel;

  @override
  String toString() {
    return "SerialPort($device, $baudrate)";
  }

  Stream<String> get dataSerial => _dataSerialBS.stream;

  bool get isConnected => _deviceConnected;

  /// Open device
  Future<bool> open() async {
    bool openResult = await _channel.invokeMethod(
        "open", {'devicePath': device.path, 'baudrate': baudrate});

    if (openResult) {
      _deviceConnected = true;
    }

    return openResult;
  }

  /// Close device
  Future<bool> close() async {
    bool closeResult = await _channel.invokeMethod("close");
    if (closeResult) {
      _dataSerialBS.close();
      _deviceConnected = false;
    }

    return closeResult;
  }

  Future<void> withoutElevatorDropWithoutSensor(String slot) async {
    await _channel.invokeMethod(
        'tcnCommand',
        jsonEncode(
            {'command': 'withoutElevatorDropWithoutSensor', "data": slot}));
  }

  Future<void> withoutElevatorDropWithSensor(String slot) async {
    await _channel.invokeMethod('tcnCommand',
        jsonEncode({'command': 'withoutElevatorDropWithSensor', "data": slot}));
  }

  Future<void> withoutElevatorTurnAll(String slot) async {
    await _channel.invokeMethod('tcnCommand',
        jsonEncode({'command': 'withoutElevatorTurnAll', "data": ''}));
  }

  Future<void> setDouble(String slot) async {
    await _channel.invokeMethod(
        'tcnCommand', jsonEncode({'command': 'setDouble', "data": slot}));
  }

  Future<void> setAllSingle() async {
    await _channel.invokeMethod(
        'tcnCommand', jsonEncode({'command': 'allSingleSlot', "data": ''}));
  }

  Future<void> setSingle(String slot) async {
    await _channel.invokeMethod(
        'tcnCommand', jsonEncode({'command': 'setSingle', "data": slot}));
  }

  Future getElevatorStatus() async {
    try {
      await _channel.invokeMethod(
          'tcnCommand', jsonEncode({'command': 'statusElevator', "data": ''}));
    } on PlatformException catch (e) {
      throw e;
    }
  }

  Future shipment(String slot) async {
    try {
      await _channel.invokeMethod(
          'tcnCommand', jsonEncode({'command': 'shipment', "data": slot}));
    } on PlatformException catch (e) {
      throw e;
    }
  }

  Future clearElevatorFault() async {
    try {
      await _channel.invokeMethod('tcnCommand',
          jsonEncode({'command': 'clearElevatorFault', "data": ''}));
    } on PlatformException catch (e) {
      throw e;
    }
  }

  Future<void> backElevatorToOrigin() async {
    return await _channel.invokeMethod('tcnCommand',
        jsonEncode({'command': 'backElevatorToOrigin', "data": ''}));
  }

  Future<void> controlTemp() async {
    return await _channel.invokeMethod(
        'tcnCommand', jsonEncode({'command': 'controlTemp', "data": ''}));
  }

  Future<void> noControlTemp() async {
    return await _channel.invokeMethod(
        'tcnCommand', jsonEncode({'command': 'noControlTemp', "data": ''}));
  }

  Future execCmd(String cmd) async {
    try {
      return await _channel.invokeMethod(
          'execCmd', jsonEncode({'command': 'execCmd', "data": cmd}));
    } on PlatformException catch (e) {
      throw e;
    }
  }

  Future sendHex(String cmd) async {
    try {
      return await _channel.invokeMethod('sendHex', jsonEncode({"data": cmd}));
    } on PlatformException catch (e) {
      throw e;
    }
  }
}

class Device {
  Device(this.name, this.path);

  String name;
  String path;

  @override
  String toString() {
    return "Device($name, $path)";
  }
}
