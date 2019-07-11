package xyz.philiprodriguez.projectroverserver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import xyz.philiprodriguez.projectrovercommunications.OnLoggableEventListener;

/**
 * This class exists to consolidate all Bluetooth nonsense into one nice area. This class should
 * deal with connection, disconnection, reconnection, sending messages, receiving messages, etc.
 */
public class BluetoothHandler {

    public static final byte[] BLUETOOTH_START_SEQUENCE = new byte[]{'a', '8', 'f', 'e', 'J', '2', '9', 'p'};

    private final OnLoggableEventListener onLoggableEventListener;
    private final OnBluetoothDisconnectedCallback onBluetoothDisconnectedCallback;
    private final OnBluetoothConnectedCallback onBluetoothConnectedCallback;

    private final String deviceMAC; //"20:16:08:10:48:03"

    private BluetoothSocket bluetoothSocket;

    private final Handler bluetoothConnectionHandler;
    private final HandlerThread bluetoothConnectionHandlerThread;
    private final Runnable bluetoothConnectionRunnable;

    private Thread bluetoothInThread;
    private Runnable bluetoothInRunnable;
    private Thread bluetoothOutThread;
    private Runnable bluetoothOutRunnable;

    // Queue for messages waiting to be sent to the Teensy
    private final BlockingQueue<byte[]> bluetoothSendQueue = new LinkedBlockingQueue<>();

    // Queue for callbacks waiting on the next voltage reading from Teensy
    private final BlockingQueue<OnVoltageValueReceivedCallback> voltageCallbacks = new LinkedBlockingQueue<>();

    public BluetoothHandler(String deviceMAC, OnBluetoothConnectedCallback onBluetoothConnectedCallback, OnBluetoothDisconnectedCallback onBluetoothDisconnectedCallback, final OnLoggableEventListener onLoggableEventListener) {
        this.deviceMAC = deviceMAC;
        this.onBluetoothConnectedCallback = onBluetoothConnectedCallback;
        this.onBluetoothDisconnectedCallback = onBluetoothDisconnectedCallback;
        this.onLoggableEventListener = onLoggableEventListener;

        this.bluetoothConnectionHandlerThread = new HandlerThread("bluetoothConnectionHandlerThread");
        this.bluetoothConnectionHandlerThread.start();
        this.bluetoothConnectionHandler = new Handler(bluetoothConnectionHandlerThread.getLooper());
        this.bluetoothConnectionRunnable = new Runnable() {
            @Override
            public void run() {
                BluetoothDevice bluetoothDevice = null;
                try {
                    // Nuke any current threads!
                    if (bluetoothInThread != null)
                        bluetoothInThread.interrupt();
                    if (bluetoothOutThread != null)
                        bluetoothOutThread.interrupt();
                    if (bluetoothSocket != null)
                        bluetoothSocket.close();

                    // Prepare bluetooth socket
                    bluetoothDevice = getBluetoothDevice();
                    bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    bluetoothSocket.connect();

                    // Prepare new in and out threads
                    bluetoothInRunnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                onLoggableEventListener.OnLoggableEvent("Bluetooth in thread started!");
                                InputStream inputStream = bluetoothSocket.getInputStream();
                                int readByte = -1;
                                outer: while (!Thread.currentThread().isInterrupted()) {
                                    try {
                                        // Read start sequence
                                        for (int i = 0; i < BLUETOOTH_START_SEQUENCE.length; i++) {
                                            readByte = inputStream.read();
                                            if (readByte < 0) {
                                                break outer;
                                            }
                                            if (readByte != BLUETOOTH_START_SEQUENCE[i]) {
                                                continue outer;
                                            }
                                        }

                                        // Start sequence OK
                                        readByte = inputStream.read();
                                        if (readByte < 0) {
                                            break outer;
                                        }
                                        byte messageType = (byte) readByte;

                                        if (messageType == 'v') {
                                            // Voltage value int
                                            ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                                            for (int i = 0; i < 4; i++) {
                                                readByte = inputStream.read();
                                                if (readByte < 0) {
                                                    break outer;
                                                }
                                                byteBuffer.put((byte) readByte);
                                            }
                                            byteBuffer.rewind();
                                            int voltageValue = byteBuffer.getInt();
                                            // Inform all currently waiting callbacks!
                                            int curSize = voltageCallbacks.size();
                                            for (int i = 0; i < curSize; i++) {
                                                voltageCallbacks.take().onVoltageValueReceived(voltageValue);
                                            }
                                        } else {
                                            onLoggableEventListener.OnLoggableEvent("Bluetooth received message of unknown type: " + messageType);
                                        }
                                    } catch (InterruptedException exc) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                onLoggableEventListener.OnLoggableEvent("Bluetooth in thread closing!");

                                if (!Thread.currentThread().isInterrupted()) {
                                    // Try to reconnect! Note this will interrupt any other threads and close the socket.
                                    bluetoothConnectionHandler.post(bluetoothConnectionRunnable);
                                }
                            }
                        }
                    };

                    bluetoothOutRunnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                onLoggableEventListener.OnLoggableEvent("Bluetooth out thread started!");
                                while (!Thread.currentThread().isInterrupted()) {
                                    try {
                                        byte[] bytesToSend = bluetoothSendQueue.take();
                                        bluetoothSocket.getOutputStream().write(bytesToSend);
                                        bluetoothSocket.getOutputStream().flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        onLoggableEventListener.OnLoggableEvent("Bluetooth IO exception! " + e.getMessage());
                                        break;
                                    }
                                }
                            } catch (InterruptedException e) {
                                onLoggableEventListener.OnLoggableEvent("Bluetooth out thread interrupted!");

                                // Re-establish interrupted flag, since the thrown InterruptedException makes the thread no longer interrupted!
                                Thread.currentThread().interrupt();
                            } finally {
                                onLoggableEventListener.OnLoggableEvent("Bluetooth out thread closing!");

                                // Try to reconnect Bluetooth!
                                if (!Thread.currentThread().isInterrupted()) {
                                    bluetoothConnectionHandler.post(bluetoothConnectionRunnable);
                                }
                            }
                        }
                    };

                    bluetoothInThread = new Thread(bluetoothInRunnable);
                    bluetoothOutThread = new Thread(bluetoothOutRunnable);

                    bluetoothInThread.start();
                    bluetoothOutThread.start();
                } catch (IOException e) {
                    onLoggableEventListener.OnLoggableEvent("Failed to establish Bluetooth connection, will retry in 5 seconds: " + e.getMessage());
                    bluetoothConnectionHandler.postDelayed(bluetoothConnectionRunnable, 5000);
                }
            }
        };

        // Kick off the connection process!
        bluetoothConnectionHandler.post(bluetoothConnectionRunnable);
    }

    /**
     * To be called once when we are DEFINITELY done using this BluetoothHandler instance. This
     * kills the HandlerThreads for connection, input, and output.
     */
    public void recycle() {
        if (bluetoothInThread != null)
            this.bluetoothInThread.interrupt();
        if (bluetoothOutThread != null)
            this.bluetoothOutThread.interrupt();
        this.bluetoothConnectionHandlerThread.quitSafely();
        if (bluetoothSocket != null) {
            try {
                this.bluetoothSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private void connectBluetooth() {
        // Which device are we connecting to?
        bluetoothConnectionHandler.post(bluetoothConnectionRunnable);
    }

    private BluetoothDevice getBluetoothDevice() throws IOException {
        onLoggableEventListener.OnLoggableEvent("Getting Bluetooth device..");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                onLoggableEventListener.OnLoggableEvent("Bluetooth is already enabled on this device...");
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                for (BluetoothDevice bluetoothDevice : pairedDevices) {
                    String deviceName = bluetoothDevice.getName();
                    String deviceMAC = bluetoothDevice.getAddress();

                    if (deviceMAC.equals(this.deviceMAC)) {
                        onLoggableEventListener.OnLoggableEvent("Found device " + deviceName + " with MAC " + deviceMAC);
                        return bluetoothDevice;
                    }
                }
                // If we didn't return yet we are in trouble!
                throw new IOException("Failed to find suitable Bluetooth device! Make sure it is paired!");
            } else {
                throw new IOException("Bluetooth is currently not enabled on the device!");
            }
        } else {
            throw new IOException("Bluetooth is not supported on this device!");
        }
    }

    public void enqueueBluetoothMessage(byte[] everythingExceptStartSequence) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(BLUETOOTH_START_SEQUENCE);
            baos.write(everythingExceptStartSequence);
            bluetoothSendQueue.add(baos.toByteArray());
            onLoggableEventListener.OnLoggableEvent("Enqueued message for " + this + "; " + bluetoothSendQueue.size());
            baos.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failure while writing to ByteArrayOutputStream: " + e.getMessage());
        }
    }

    public void addOnVoltageValueReceivedCallback(OnVoltageValueReceivedCallback onVoltageValueReceivedCallback) {
        voltageCallbacks.add(onVoltageValueReceivedCallback);
    }
}
