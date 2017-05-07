package pl.edu.agh.doppler;

import java.io.IOException;
import java.io.OutputStream;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class Pub {
    public static BluetoothSocket btSocket;
    public static OutputStream outStream;
    //public static String IP_Address;

    public static void sendMessage(String msg){
        if(outStream == null) return;
        try{
            outStream.write(msg.getBytes());
        } catch(IOException e){
            Log.d("fuck","Send msg "+msg+" failed");
        }
    }
}