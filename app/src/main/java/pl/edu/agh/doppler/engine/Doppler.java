package pl.edu.agh.doppler.engine;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import pl.edu.agh.doppler.MainActivity;
import pl.edu.agh.doppler.Pub;
import pl.edu.agh.doppler.fft.FFT;

public class Doppler {

    /** Inner instance. We want it to be sun*/
    public static Doppler doppler;
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String TAG = "doppler!!";
    public BluetoothAdapter mBluetoothAdapter = null;
    public BluetoothDevice mDevice = null;

    //prelimiary frequency stuff
    public static final float PRELIM_FREQ = 20000;
    public static final int PRELIM_FREQ_INDEX = 20000;
    public static final int MIN_FREQ = 19000;
    public static final int MAX_FREQ = 21000;


    public static final int RELEVANT_FREQ_WINDOW = 33;

    //modded from the soundwave paper. frequency bins are scanned until the amp drops below
    // 1% of the primary tone peak
    public static final double MAX_VOL_RATIO_DEFAULT = 0.1;
    public static final double SECOND_PEAK_RATIO = 0.3;
    public static double maxVolRatio = MAX_VOL_RATIO_DEFAULT;

    //for bandwidth positions in array
    public static final int LEFT_BANDWIDTH = 0;
    public static final int RIGHT_BANDWIDTH = 1;

    //I want to add smoothing
    public static final float SMOOTHING_TIME_CONSTANT = 0.5f;

    //utility variables for reading and parsing through audio data.
    /** Microphone reference. */
    public AudioRecord microphone;

    /** Tone player. */
    public Player player;

    /**
     * Sampling frequency - 44,1kHz
     * @see <a href="http://pl.wikipedia.org/wiki/Próbkowanie">Wikipedia</a>
     */
    public static final int SAMPLE_RATE = 44100;

    public int frequencyIndex;

    /** Buffer for reading microphone data. */
    public short[] buffer;

    /** Array for data passed to fft. Contains scaled {@link #buffer} data. */
    public float[] fftBuffer;

    /** Holds the freqs of the previous iteration. */
    public float[] oldFrequencies;

    /** Buffer size. */
    public int bufferSize;

    /** Handler used to run move detecting in background. */
    public Handler mHandler;

    public boolean repeat;

    /** Fast fourier transform. */
    public FFT fft;

    /** Calibrator. */
    public Calibrator calibrator;

    /** Gestures listener. */
    public OnGestureListener gestureListener;

    /** Previous move direction. */
    public int previousDirection = 0;

    /** Counter for direction changes. */
    public int directionChanges;

    /** Cycles left to read. */
    public int cyclesLeftToRead = -1;

    /** Cycles left to start recording detecting. */
    public int cyclesToRefresh;

    /** Returns singleton instance of doppler object. */
    public static Doppler getDoppler() {
        if(doppler == null) {
            doppler = new Doppler();
        }
        return doppler;
    }

    /** Constructor. For initializing variables. */
    public Doppler() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent mIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        }

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
            Log.d(TAG,"套接字创建失败！");
        }

        Log.d(TAG,"成功连接智能小车！可以开始操控了~~~");
        mBluetoothAdapter.cancelDiscovery();
        try {
            Pub.btSocket.connect();
            Log.d(TAG,"连接成功建立，数据连接打开！");
        } catch (IOException e) {
            try {
                Pub.btSocket.close();
            } catch (IOException e2) {
                Log.d(TAG,"连接没有建立，无法关闭套接字！");
            }
        }
        try {
            Log.d(TAG,"try to get output");
            Pub.outStream = Pub.btSocket.getOutputStream();
        } catch (IOException e) {
        }


        //write a check to see if stereo is supported
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        buffer = new short[bufferSize];

        frequencyIndex = PRELIM_FREQ_INDEX;

        player = new Player(PRELIM_FREQ);

        microphone = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        mHandler = new Handler();

        calibrator = new Calibrator();
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

    /** Sets frequency index. */
    public void setFrequency(float frequency) {
        this.frequencyIndex = fft.freqToIndex(frequency);
    }

    /**
     * Starts recording and detecting.
     *
     * @return true if started, false when error occurred
     */
    public boolean start() {
        player.play();

        try {
            //you might get an error here if another app hasn't released the microphone
            microphone.startRecording();
            repeat = true;

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    optimizeFrequency(MIN_FREQ, MAX_FREQ);
                    //assuming fft.forward was already called;
                    readMic();
                }
            }, 1000);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Doppler", e.getMessage());
            return false;
        }

        int bufferReadResult = microphone.read(buffer, 0, bufferSize);
        bufferReadResult = getHigherTwoPower(bufferReadResult);
        //get higher p2 because buffer needs to be "filled out" for FFT
        fftBuffer = new float[getHigherTwoPower(bufferReadResult)];
        fft = new FFT(getHigherTwoPower(bufferReadResult), SAMPLE_RATE);

        return true;
    }

    public int[] getBandwidth() {
        readAndFFT();

        //rename this
        int primaryTone = frequencyIndex;
        double normalizedVolume;
        double primaryVolume = fft.getBand(primaryTone);
        int leftBandwidth = 0;

        do {
            leftBandwidth++;
            double volume = fft.getBand(primaryTone - leftBandwidth);
            normalizedVolume = volume / primaryVolume;
            //Log.d("DOPPLER", "primaryVol:" + primaryVolume + " vol:" + volume + " norm:" + normalizedVolume);
        } while(normalizedVolume > maxVolRatio && leftBandwidth < RELEVANT_FREQ_WINDOW);


        //secondary bandwidths are for looking past the first minimum to search for "split off" peaks, as per the paper
        int secondScanFlag = 0;
        int secondaryLeftBandwidth = leftBandwidth;

        //second scan
        do {
            secondaryLeftBandwidth++;
            double volume = fft.getBand(primaryTone - secondaryLeftBandwidth);
            normalizedVolume = volume / primaryVolume;

            if(normalizedVolume > SECOND_PEAK_RATIO) {
                secondScanFlag = 1;
            }

            if(secondScanFlag == 1 && normalizedVolume < maxVolRatio) {
                break;
            }
        } while(secondaryLeftBandwidth < RELEVANT_FREQ_WINDOW);

        if(secondScanFlag == 1) {
            leftBandwidth = secondaryLeftBandwidth;
        }

        int rightBandwidth = 0;

        do {
            rightBandwidth++;
            double volume = fft.getBand(primaryTone + rightBandwidth);
            normalizedVolume = volume / primaryVolume;
        } while(normalizedVolume > maxVolRatio && rightBandwidth < RELEVANT_FREQ_WINDOW);

        secondScanFlag = 0;
        int secondaryRightBandwidth = 0;
        do {
            secondaryRightBandwidth++;
            double volume = fft.getBand(primaryTone + secondaryRightBandwidth);
            normalizedVolume = volume / primaryVolume;

            if(normalizedVolume > SECOND_PEAK_RATIO) {
                secondScanFlag = 1;
            }

            if(secondScanFlag == 1 && normalizedVolume < maxVolRatio) {
                break;
            }
        } while(secondaryRightBandwidth < RELEVANT_FREQ_WINDOW);

        if(secondScanFlag == 1) {
            rightBandwidth = secondaryRightBandwidth;
        }

        return new int[] {leftBandwidth, rightBandwidth};

    }

    /**
     * Reads data from microphone.
     * Calls itself recursively while {@link #repeat}
     */
    public void readMic() {
        Log.d("DOPPLER", "readMic");
        int[] bandwidths = getBandwidth();
        int leftBandwidth = bandwidths[LEFT_BANDWIDTH];
        int rightBandwidth = bandwidths[RIGHT_BANDWIDTH];

        callGestureCallback(leftBandwidth, rightBandwidth);

        maxVolRatio = calibrator.calibrate(maxVolRatio, leftBandwidth, rightBandwidth);

        if(repeat) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    readMic();
                }
            });
        }
    }

    /** Sets listener for movement detection. */
    public void setGestureListener(final OnGestureListener gestureListener) {
        this.gestureListener = gestureListener;
    }

    /**
     * Pause detecting.
     *
     * @return true if succeed, false when error occurred
     */
    public boolean pause() {
        try {
            microphone.stop();
            player.pause();
            repeat = false;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Calls appropriate method of {@link #gestureListener} when event occurs.
     *
     * @param leftBandwidth left bandwidth value
     * @param rightBandwidth right bandwidth value
     */
    public void callGestureCallback(final int leftBandwidth, final int rightBandwidth) {
        //early escape if need to refresh
        if(gestureListener == null || cyclesToRefresh > 0) {
            cyclesToRefresh--;
            return;
        }

        int cyclesToRead = 5;
        if(leftBandwidth > 4 || rightBandwidth > 4) {
            Log.d("DOPPLER", "left:" + leftBandwidth + " right:" + rightBandwidth);
            //implement gesture logic
            int difference = leftBandwidth - rightBandwidth;
            int direction = (int) Math.signum(difference);

            if(direction != 0 && direction != previousDirection) {
                //scan a 4 frame window to wait for taps or double taps
                cyclesLeftToRead = cyclesToRead;
                previousDirection = direction;
                directionChanges++;
            }
        }

        cyclesLeftToRead--;

        if(cyclesLeftToRead == 0) {
            if(directionChanges == 1) {
                if(previousDirection == -1) {
                    Log.d("DOPPLER", "PUSH!");
                    Pub.sendMessage("ONA");

                    gestureListener.onPush();
                } else {
                    Log.d("DOPPLER", "PULL!");
                    Pub.sendMessage("ONB");
                    gestureListener.onPull();
                }
            } else if(directionChanges == 2) {
                Log.d("DOPPLER", "TAP!");
                gestureListener.onTap();
            } else {
                Log.d("DOPPLER", "2 x TAP!");
                gestureListener.onDoubleTap();
            }
            previousDirection = 0;
            directionChanges = 0;
            cyclesToRefresh = cyclesToRead;
        } else {
            gestureListener.onNothing();
        }
    }

    /**
     * Smooths out freq
     */
    public void smoothOutFrequencies() {
        for(int i = 0; i < fft.specSize(); ++i) {
            float smoothedOutMag = SMOOTHING_TIME_CONSTANT * fft.getBand(i) + (1 - SMOOTHING_TIME_CONSTANT) * oldFrequencies[i];
            fft.setBand(i, smoothedOutMag);
        }
    }

    /**
     * Searches for frequency with maximum frequency.
     *
     * @param minFreq minimal frequency
     * @param maxFreq maximum frequency
     */
    public void optimizeFrequency(int minFreq, int maxFreq) {
        readAndFFT();
        int minInd = fft.freqToIndex(minFreq);
        int maxInd = fft.freqToIndex(maxFreq);

        int primaryInd = frequencyIndex;
        for(int i = minInd; i <= maxInd; ++i) {
            if(fft.getBand(i) > fft.getBand(primaryInd)) {
                primaryInd = i;
            }
        }

        setFrequency(fft.indexToFreq(primaryInd));
        Log.i("DOPPLER", "Frequency optimized idx:" + frequencyIndex + " frequency" + fft.indexToFreq(primaryInd));
    }

    /**
     * Reads data from microphone.
     * Applies Hanning windowing and then fft.
     * On the end smooths out frequencies.
     *
     * @see <a href="http://dsp.stackexchange.com/questions/11312/why-should-one-use-windowing-functions-for-fft">
     *     Why should we use windowing function for FFT</a>
     */
    public void readAndFFT() {
        //copy into old freqs array
        if(fft.specSize() != 0 && oldFrequencies == null) {
            oldFrequencies = new float[fft.specSize()];
        }
        for(int i = 0; i < fft.specSize(); ++i) {
            oldFrequencies[i] = fft.getBand(i);
        }

        int bufferReadResult = microphone.read(buffer, 0, bufferSize);
        //Log.d("DOPPLER", "Shorts read: " + bufferReadResult);

        for(int i = 0; i < bufferReadResult; i++) {
            fftBuffer[i] = (float) buffer[i] / Short.MAX_VALUE;
        }

        //apply windowing
        for(int i = 0; i < bufferReadResult; ++i) {
            // Calculate & apply window symmetrically around center point
            // Hanning (raised cosine) window
            //float windowValue = (float) (0.5 + 0.5 * Math.cos(Math.PI * (float) i / (float) (bufferReadResult / 2)));
            //if(i > bufferReadResult / 2) {
            //    windowValue = 0;
            //}
            //fftBuffer[bufferReadResult / 2 + i] *= windowValue;
            //fftBuffer[bufferReadResult / 2 - i] *= windowValue;
            fftBuffer[i] = (float) (fftBuffer[i] * 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / bufferReadResult)));
        }

        fft.forward(fftBuffer);

        //apply smoothing
        smoothOutFrequencies();
    }

    /**
     * compute nearest higher power of two
     * @see <a href="http://www.graphics.stanford.edu/~seander/bithacks.html">Round up to the next highest power of 2</a>
     */
    public int getHigherTwoPower(int val) {
        val--;
        val |= val >> 1;
        val |= val >> 2;
        val |= val >> 8;
        val |= val >> 16;
        val++;
        return (val);
    }

    /**
     * Listener for most common gesture types.
     */
    public interface OnGestureListener {
        /** On swipe towards. */
        void onPush();

        /** On swipe away. */
        void onPull();

        /** On tap. */
        void onTap();

        /** On double tap. */
        void onDoubleTap();

        /** When no movement is detected. */
        void onNothing();
    }

    public class Calibrator {

        public final static int CYCLE_SIZE = 20;

        public final static int UP_THRESHOLD = 5;

        public final static int DOWN_THRESHOLD = 0;

        public final static double UP_AMOUNT = 1.1;

        public final static double DOWN_AMOUNT = 0.9;

        public final static double MAX = 0.95;

        public final static double MIN = 0.0001;

        public int i = 0;

        /** Direction in which move was detected last time. Used to detect direction changes. */
        public int previousDirection = 0;

        /** Counter for direction changes. */
        public int directionChanges = 0;

        /**
         * Calibrates volume ratio.
         *
         * @param maxVolRatio actual volume ratio
         * @param leftBandwidth left bandwidth value
         * @param rightBandwidth right bandwidth value
         *
         * @return new maximum volume ration
         */
        public double calibrate(double maxVolRatio, final int leftBandwidth, final int rightBandwidth) {
            //Log.d("DOPPLER","Start calibrating.");
            //calculate difference between bandwidths
            int difference = leftBandwidth - rightBandwidth;
            //calculate direction
            int direction = (int) Math.signum(difference);

            //if direction change occurred take appropriate steps
            if(previousDirection != direction) {
                directionChanges++;
                previousDirection = direction;
            }

            //if cycle finished
            i = (i + 1) % CYCLE_SIZE;
            if(i == 0) {
                if(directionChanges >= UP_THRESHOLD) {
                    maxVolRatio *= UP_AMOUNT;
                } else if (directionChanges == DOWN_THRESHOLD) {
                    maxVolRatio *= DOWN_AMOUNT;
                }

                //apply boundaries
                maxVolRatio = maxVolRatio > MAX ? MAX : maxVolRatio;
                maxVolRatio = maxVolRatio < MIN ? MIN : maxVolRatio;

                //reset direction changes counter
                directionChanges = 0;
            }

            //Log.d("DOPPLER","End calibrating.");
            return maxVolRatio;
        }
    }
}
