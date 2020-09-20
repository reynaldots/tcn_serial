package io.idhea.tcnserial;

import android.app.Activity;
import android.serialport.SerialPort;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.Thread;
import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

/** TcnserialPlugin */
public class TcnserialPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, RequestPermissionsResultListener, EventChannel.StreamHandler {
  private static final String TAG = "TCNSerial";
  private MethodChannel channel;
  private EventChannel eventChannel;

  private EventChannel.EventSink mEventSink;
  protected OutputStream mOutputStream;
  private InputStream mInputStream;
  private ReadThread mReadThread;
  private Handler mHandler = new Handler(Looper.getMainLooper());
  protected SerialPort mSerialPort;
  private FlutterPluginBinding pluginBinding;
  private static TcnserialPlugin instance;
  private static final String NAMESPACE = "io.idhea.tcnserial/tcnserial";
  private ActivityPluginBinding activityBinding;
  private Activity activity;

  public TcnserialPlugin() {
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    pluginBinding = flutterPluginBinding;
  }

  public static void registerWith(Registrar registrar) {
    if (instance == null) {
      instance = new TcnserialPlugin();
    }
    Activity activity = registrar.activity();
    instance.setup(registrar.messenger(), registrar, activity);
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activityBinding = binding;
    setup(pluginBinding.getBinaryMessenger(), null, activityBinding.getActivity());
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    tearDown();
  }


  private void setup(final BinaryMessenger messenger, final PluginRegistry.Registrar registrar, final Activity activity) {
      this.activity = activity;
      channel = new MethodChannel(messenger, NAMESPACE + "/methods");
      channel.setMethodCallHandler(this);
      eventChannel = new EventChannel(messenger, NAMESPACE + "/event");
      eventChannel.setStreamHandler(this);
      if (registrar != null) {
        // V1 embedding setup for activity listeners.
        registrar.addRequestPermissionsResultListener(this);
      } else {
        // V2 embedding setup for activity listeners.
        activityBinding.addRequestPermissionsResultListener(this);
      }
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;
      case "open":
        try {
          final String devicePath = call.argument("devicePath");
          final int baudrate = call.argument("baudrate");
          Boolean openResult = openDevice(devicePath, baudrate);
          result.success(openResult);
        }  catch (JSONException e) {
          e.printStackTrace();
          result.error("Error open", e.getMessage(), null);
        }
        break;
      case "close":
        try {
          Boolean closeResult = closeDevice();
          result.success(closeResult);
        } catch (JSONException e) {
          e.printStackTrace();
          result.error("Error close", e.getMessage(), null);
        }
        break;

      case "tcnCommand":
        try {
          JSONObject obj = new JSONObject((String) call.arguments());
          processTCNCommand(obj);
          result.success(true);
        } catch (JSONException e) {
          e.printStackTrace();
          result.error("Error tcnCommand", e.getMessage(), null);
        }
        break;
      case "execAdb":
        try {
          JSONObject obj = new JSONObject((String) call.arguments());
          execAdb((String) obj.get("data"));
          result.success(true);
        } catch (JSONException e) {
          e.printStackTrace();
          result.error("Error execAdb", e.getMessage(), null);
        }
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  public static void execAdb(String command) {
    Process process = null;
    String commandString;

    commandString = String.format("%s", "adb " + command);

    System.out.print("Command is " + commandString + "\n");
    try {
      process = Runtime.getRuntime().exec(commandString);

      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.print(line + "\n");
      }

    } catch (IOException e) {
      System.out.print("error execAdb" + e.getMessage());
    }
  }

  private void getStatusElevator() {
    byte[] bytesToSend = {0x02,0x03,0x01,0x00,0x00,0x03,0x03};
    try {
      mOutputStream.write(bytesToSend, 0, 7);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void processTCNCommand(JSONObject obj) {
    try {
      byte[] bytesToSend;
      int data = 0; //aqui vem o dado: SLOT ou TEMPERATURA
      String command = (String) obj.get("command");
      switch (command) {

        case "withoutElevatorDrop1withoutSensor"://ok
          bytesToSend = new byte[]{0x00, (byte)0xFF ,0x01 ,(byte)0xFE ,0x55 ,(byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "withoutElevatorDropWithoutSensor"://ok
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF ,(byte)(data), (byte)(0xFF-(byte)(data)),0x55 ,(byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "withoutElevatorDrop1WithSensor"://ok
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,0x01, (byte)0xFE ,(byte)0xAA ,0x55 };
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "withoutElevatorDropWithSensor"://sem elevador é esseee!! entrega detectando queda pelo sensor
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,(byte)(data), (byte)(0xFF-(byte)(data)) ,(byte)0xAA ,0x55 };
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "withoutElevatorTurnAll"://sem elevador - reset command - testa todos os slot girando eles
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,0x65, (byte)0x9A ,0x55 ,(byte)0xAA };
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        // case "withoutElevatorClearError3"://sem elevador - limpa error 3 n funfiona
        //   bytesToSend = new byte[]{0x02 ,(byte)0x03 ,0x50, (byte)0x03 ,0x02 ,(byte)0x03,(byte)0x03 };
        //   mOutputStream.write(bytesToSend, 0, 7);
        //   break;

        // case "withoutElevatorClearError"://sem elevador - limpa error n funcoina
        //   data = Integer.parseInt((String) obj.get("data"));
        //   bytesToSend = new byte[]{0x00 ,(byte)0xFF ,0x65, (byte)0x9A ,0x55 ,(byte)0xAA };
        //   mOutputStream.write(bytesToSend, 0, 6);
        //   break;

        // case "aaaaa222":
        //   data = Integer.parseInt((String) obj.get("data"));
        //   bytesToSend = new byte[]{0x00, (byte)0xFF, 0x01, (byte)0xFE, (byte)(data), (byte)(0xFF-(byte)(data))};
        //   mOutputStream.write(bytesToSend, 0, 6);
        //   break;

        // case "aaaaa2223":
        //   data = Integer.parseInt((String) obj.get("data"));
        //   bytesToSend = new byte[]{0x00, (byte)0xFF, 0x01, (byte)(data), (byte)(data), (byte)(0xFF-(byte)(data))};
        //   mOutputStream.write(bytesToSend, 0, 6);

        // case "aaaaa2224":
        //   data = Integer.parseInt((String) obj.get("data"));
        //   bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)(data), (byte)0xFE, (byte)(data), (byte)(0xFF-(byte)(data))};
        //   mOutputStream.write(bytesToSend, 0, 6);
        //   break;


        /*case "setDoublet1"://tipo 1
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00,(byte)0xff,(byte)(data),(byte) 0xFF, (byte)0xCA,0x55};
          mOutputStream.write(bytesToSend, 0, 6);
        break;
        case "setDoublet2"://tipo 2
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)data,(byte)(0xFF-(byte)(data)),0x55, (byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
        break;
        case "setDoublet3"://tipo 3
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCA,(byte)(0xFF-(byte)(0xCA)),0x55, (byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "setDoublet4"://tipo 4
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCA,(byte)(0xFF-(byte)(data)),0x55, (byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "set1double":
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCA,0x35,0x01, (byte)0xFE};
          mOutputStream.write(bytesToSend, 0, 6);
          break;*/

        case "allSingleSlot"://ok
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCB, 0x34 ,0x55 ,(byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;


        case "statusDoor"://ok
        bytesToSend = new byte[]{0x00, (byte)0xFF,(byte) 0xDF ,0x20 ,0x55 ,(byte)0xAA};
        mOutputStream.write(bytesToSend, 0, 6);
        break;

        case "closeDoor"://ok
        bytesToSend = new byte[]{0x02, (byte)0x04,(byte) 0xDF ,0x20 ,0x55 ,(byte)0xAA};
        mOutputStream.write(bytesToSend, 0, 6);
        break;


     /*   case "gateStatusClose":
          bytesToSend = new byte[]{0x00 ,0x5D ,0x00, 0x00, 0x5D };//close
          mOutputStream.write(bytesToSend, 0, 5);
          break;
        case "doorStatusOpen":
          bytesToSend = new byte[]{ 0x00 ,0x5D ,0x01 ,0x00 ,0x5E };//open
          mOutputStream.write(bytesToSend, 0, 5);
          break;*/

        case "openBuzzer"://????
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xDE, 0x21 ,(byte)0xAA ,0x55};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "closeBuzzer"://???????
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,(byte)0xDE, 0x21, 0x55 ,(byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;



        case "glassHeatingOn"://???
          bytesToSend = new byte[]{0x00, (byte)0xFF ,(byte)0xD4,0x2B ,0x01,(byte) 0xFE};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "glassHeatingOff"://???
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,(byte)0xD4 ,0x2B, 0x00 ,(byte)0xFF};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "lightOn"://ok
          bytesToSend = new byte[]{0x00, (byte)0xFF ,(byte)0xDD ,0x22,(byte)0xAA ,0x55};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "lightOff"://ok
          bytesToSend = new byte[]{0x00, (byte)0xFF ,(byte)0xDD ,0x22 ,0x55,(byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "heatingMode"://???
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,(byte)0xCD ,0x32 ,0x01 ,(byte)0xFE};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "refriMode"://????
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,(byte)0xCD ,0x32 ,0x01 ,(byte)0xFE};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "setDouble"://ok
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCA, 0x35, (byte)(data), (byte)(0xFF-(byte)(data))};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "setSingle":
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00 ,(byte)0xFF, (byte)0xC9 ,0x36 ,(byte)(data), (byte)(0xFF-(byte)(data))};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "set1And2Single":
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00 ,(byte)0xFF, (byte)0xC9 ,0x36 ,0x01, (byte)0xFE};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "getTemp":
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xDC,0x23,0x55, (byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "noControlTemp"://ok
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCC,0x33,0x00, (byte)0xFF};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "controlTemp"://ok
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCC,0x33,0x01, (byte)0xFE};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "defrostingTimeFor20min"://??
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,(byte)0xD1 ,0x2E ,0x14,(byte)0xEB};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "120MinutesCompressor":
          bytesToSend = new byte[]{0x00 ,(byte)0xFF, (byte)0xD2 ,0x2D ,0x78,(byte) 0x87};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "temperatureDifferenceSetTo2"://??
          bytesToSend = new byte[]{0x00,(byte) 0xFF, (byte)0xCF, 0x30,0x02, (byte)0xFD};
          mOutputStream.write(bytesToSend, 0, 6);
          break;


        case "temperatureCompensationSetTo1":///???
          bytesToSend = new byte[]{0x00 ,(byte)0xFF ,(byte)0xD0 ,0x2F ,0x01, (byte)0xFE};
          mOutputStream.write(bytesToSend, 0, 6);
          break;

        case "setTemp"://ok
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCE, 0x31, (byte)(data), (byte)(0xFF-(byte)(data))};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "setTempTo5"://ok
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)0xCE,0x31,0x05, (byte)0xFA};
          mOutputStream.write(bytesToSend, 0, 6);
          break;
        case "checkvalid1"://check is valid 1 - ok
          data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x00, (byte)0xFF, (byte)(0x78+(byte)(data)),(byte)(0xFF-(byte)(0x78+(byte)(data))),0x55, (byte)0xAA};
          mOutputStream.write(bytesToSend, 0, 6);
        break;

        
        case "driveBoardStatus":
          bytesToSend = new byte[]{0x02 ,0x03 ,0x52,0x00 ,0x00,0x03 ,0x03 };
          mOutputStream.write(bytesToSend, 0, 7);
          break;

        case "statusElevator":
          getStatusElevator();
        break;
        case "shipment":


           data = Integer.parseInt((String) obj.get("data"));
          bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte)(data),0x00,0x00,(byte) (byte)(data),0x03, 0x05};
          mOutputStream.write(bytesToSend, 0, 10);
          
          break;
        case "t"://teste
          String[] codes = ((String) obj.get("data")).split(",");
          byte[] bytesToSend2 = new byte[codes.length];
        
          for (Integer i = 0; i < codes.length; i++) {            
              String hex = Integer.toHexString(Integer.parseInt(codes[i]));
              bytesToSend2[i] =        Byte.parseByte(hex,16);
          }
          // bytesToSend = new byte[]{0x02 ,0x03 ,0x52,0x00 ,0x00,0x03 ,0x03 };
          mOutputStream.write(bytesToSend2, 0, codes.length);
          break;

        case "d":
          String[] range = ((String) obj.get("data")).split("-");


          for (int i = (Integer.parseInt(range[0])); i <= (Integer.parseInt(range[1])); i++) {
            bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte)(Integer.parseInt(range[2])),0x00,0x00,(byte) (byte)(Integer.parseInt(range[2])),0x03, (byte)i};
            mOutputStream.write(bytesToSend, 0, 10);
            // Thread.sleep(200);
          }

          break;

        case "d2":
          int slot3 = Integer.parseInt((String) obj.get("data"));
          int adjust3 = slot3 + 0 + 0 + 0;

          bytesToSend = new byte[]{0x02,0x06,0x02,0x00, (byte) slot3,0x00,0x00,(byte) adjust3,0x03, (byte)slot3};
          mOutputStream.write(bytesToSend, 0, 10);
          // Thread.sleep(200);


          break;


        case "clearElevatorFault"://clearElevatorFault
          /*bytesToSend = new byte[]{0x02,0x03,(byte) Integer.parseInt((String) obj.get("data")),0x00, 0x00, 0x03, 0x03};
          mOutputStream.write(bytesToSend, 0, 7);*/

          /*bytesToSend = new byte[]{0x02,0x03, 0x50,0x00, 0x00, 0x03, 0x03};
          mOutputStream.write(bytesToSend, 0, 7);*/

          // for (int i = 0; i < 100; i++) {
          //   bytesToSend = new byte[]{0x02,0x03, 0x50,0x00, 0x00, 0x03, (byte)i};
          //   mOutputStream.write(bytesToSend, 0, 7);
          //   Thread.sleep(200);
          // }

          bytesToSend = new byte[]{0x02, 0x03, 0x50, 0x00, 0x00, 0x03, (byte)82};
          mOutputStream.write(bytesToSend, 0, 7);
          // Thread.sleep(200);

          break;
        case "backElevatorToOrigin"://backElevatorToOrigin
          bytesToSend = new byte[]{0x02, 0x03, 0x05,0x00, 0x00, 0x03, 0x05};
              mOutputStream.write(bytesToSend, 0, 7);
            // Thread.sleep(200);

          // for (int i = 0; i < 100; i++) {
          //   bytesToSend = new byte[]{0x02,0x03, 0x05,0x00, 0x00, 0x03, (byte)i};
          //   mOutputStream.write(bytesToSend, 0, 7);
          //   Thread.sleep(200);
          // }
          break;
      }

      /*
      *
A faixa de cálculo BCC inclui: STX + comprimento do pacote de comunicação + comando + pacote de dados + ETX
* 2 + X + 2 + 3 = 7
      * */

      /*byte[] bytesToSend0 = {0x00, (byte) 0xFF, (byte) 0x83, (byte) 0xAC, 0x55, (byte) 0xAA};
      mOutputStream.write(bytesToSend0, 0, 10);
      Log.e(TAG, "Write data" + Arrays.toString(bytesToSend0));
      Thread.sleep(1000);

      byte[] bytesToSend = {0x02,0x06,0x02,0x00,0x05,0x00,0x00,0x01,0x03,0x05};
      mOutputStream.write(bytesToSend, 0, 10);
      Log.e(TAG, "Write data" + Arrays.toString(bytesToSend));*/
/*
      byte[] bytesToSend = {0x02,0x03,0x01,0x00,0x00,0x03,0x03};
      Log.e(TAG, "Write data 1" + Arrays.toString(bytesToSend));
      mOutputStream.write(bytesToSend, 0, 7);
      Thread.sleep(1000);


      byte[] bytesToSend2 = {0x02,0x06,0x02 ,0x00, 0x05 ,0x00 ,0x00 ,0x01, 0x03, 0x05};
      Log.e(TAG, "Write data 2" + Arrays.toString(bytesToSend2));
      mOutputStream.write(bytesToSend2, 0, 10);
      Thread.sleep(1000);
*/

    } catch (IOException e) {
      Log.e(TAG, e.toString());
    } catch (JSONException e) {
      Log.e(TAG, e.toString());
    }
  }

  private Boolean openDevice(String devicePath, int baudrate) {
    if (mSerialPort == null) {
      /* Check parameters */
      if ((devicePath.length() == 0) || (baudrate == -1)) {
        return false;
      }

      /* Open the serial port */
      try {
        mSerialPort = new SerialPort(new File(devicePath), baudrate, 0);
        mOutputStream = mSerialPort.getOutputStream();
        mInputStream = mSerialPort.getInputStream();
        mReadThread = new ReadThread();
        mReadThread.start();
        return true;
      } catch (Exception e) {
        Log.e(TAG, e.toString());
        return false;
      }
    }
    return false;
  }

  private Boolean closeDevice() {
    if (mSerialPort != null) {
      mSerialPort.close();
      mSerialPort = null;
      return true;
    }
    return false;
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    pluginBinding = null;
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    return false;
  }

  private class ReadThread extends Thread {
    @Override
    public void run() {
      super.run();
      while (!isInterrupted()) {
        int size;
        try {
          byte[] buffer = new byte[64];
          if (mInputStream == null)
            return;
          size = mInputStream.read(buffer);
          if (size > 0) {
            onDataReceived(buffer, size);
          }
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }
      }
    }
  }

  protected void onDataReceived(final byte[] buffer, final int size) throws UnsupportedEncodingException {
    Log.i(TAG, bytesToHex(Arrays.copyOfRange(buffer, 0, size)));
    invokeMethodUIThread("dataSerial", bytesToHex(Arrays.copyOfRange(buffer, 0, size)));
  }

  private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
      char[] hexChars = new char[bytes.length * 2];
      for ( int j = 0; j < bytes.length; j++ ) {
          int v = bytes[j] & 0xFF;
          hexChars[j * 2] = hexArray[v >>> 4];
          hexChars[j * 2 + 1] = hexArray[v & 0x0F];
      }
      return new String(hexChars);
  }

  private void invokeMethodUIThread(final String name, final String result) {
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        channel.invokeMethod(name, result);
      }
    });
  }

  @Override
  public void onListen(Object o, EventChannel.EventSink eventSink) {
    mEventSink = eventSink;
  }

  @Override
  public void onCancel(Object o) {
    mEventSink = null;
  }

  private void tearDown() {
    Log.i(TAG, "teardown");
    activityBinding.removeRequestPermissionsResultListener(this);
    activityBinding = null;
    channel.setMethodCallHandler(null);
    channel = null;
    eventChannel.setStreamHandler(null);
  }

}
