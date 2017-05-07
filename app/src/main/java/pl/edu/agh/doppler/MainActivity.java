package pl.edu.agh.doppler;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import pl.edu.agh.doppler.engine.Doppler;


public class MainActivity extends AppCompatActivity {

    public Doppler doppler;
    TextView text ;
    public BluetoothAdapter mBluetoothAdapter = null;
    public BluetoothDevice mDevice = null;
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public String TAG = "mainactivity";
    public Button button;
    public Button button2;
    public Button button3;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        text = (TextView)findViewById(R.id.text);
        final Context context = getApplicationContext();


        button=(Button)findViewById(R.id.button);
        button2 = (Button)findViewById(R.id.button2);
        button3 = (Button)findViewById(R.id.button3);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                bluetoothSender("ONA");
            }
        });

        button2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                bluetoothSender("ONB");
            }
        });
        button3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                bluetoothSender("ONF");
            }
        });

        Log.d(TAG,"begin");
/*
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "不支持蓝牙", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent mIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(mIntent, 1);
        }
*/
        doppler = Doppler.getDoppler();
        doppler.start();

        doppler.setGestureListener(new Doppler.OnGestureListener() {
            @Override
            public void onPush() {
                text.setText("Push!!!");
                bluetoothSender("ONA");

                //bluetoothSender(msg);
               // Toast.makeText(context, R.string.push, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPull() {
                text.setText("Pull!!!");
                bluetoothSender("NOB");
                //bluetoothSender(msg);
                //Toast.makeText(context, R.string.pull, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTap() {
                text.setText("Tap!!!");

              //  Toast.makeText(context, R.string.tap, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDoubleTap() {
                text.setText("DoubleTap!!!");
                //Toast.makeText(context, R.string.double_tap, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothing() {

            }
        });
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        doppler.pause();
    }



    public void bluetoothSender(String command){
        byte[] msgBuffer;
        msgBuffer = command.getBytes();
        try {
            Pub.outStream.write(msgBuffer);
            Log.d(TAG,"send msg "+command);
        } catch (IOException e) {
        }
    }


    public void DisplayToast(String str) {
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 220);
        toast.show();
    }

    protected void onResume() {
        super.onResume();
/*
        DisplayToast("正在尝试连接智能小车，请稍后····");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice d : pairedDevices) {
            if (d.getName().equals("HC-06")) {
                mDevice = d;
                Log.d(TAG,"now get hc");
                Log.e("App", d.getName());
                break;
            }
        }
        try {
            Log.d("wa","i am here");
            Pub.btSocket = mDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            DisplayToast("套接字创建失败！");
        }

        DisplayToast("成功连接智能小车！可以开始操控了~~~");
        mBluetoothAdapter.cancelDiscovery();
        try {
            Pub.btSocket.connect();
            DisplayToast("连接成功建立，数据连接打开！");
        } catch (IOException e) {
            try {
                Pub.btSocket.close();
            } catch (IOException e2) {
                DisplayToast("连接没有建立，无法关闭套接字！");
            }
        }
        try {
            Log.d(TAG,"try to get output");
            Pub.outStream = Pub.btSocket.getOutputStream();
        } catch (IOException e) {
        }
*/
        doppler.start();
    }
}
