package xyz.philiprodriguez.projectrovercommunications;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ReceiverThread extends Thread {
    public static final String CLASS_IDENTIFIER = "ReceiverThread";

    // This is the maximum message size allowed for reception.
    public static final int MAX_MESSAGE_SIZE = 1024*1024*64; // 64MB

    // This is the start sequence to be prepended before all messages.
    public static final byte[] START_SEQUENCE = new byte[]{127, 65, 27, 94, 56, 23, 19, 122, 12, 56, 32, 49};

    private final Socket clientSocket;
    private final OnThreadFinishedListener onThreadFinishedListener;

    private volatile OnFrameReceivedListener onFrameReceivedListener;
    private volatile OnLoggableEventListener onLoggableEventListener;
    private volatile OnMotorStateMessageReceivedListener onMotorStateMessageReceivedListener;
    private volatile OnServerSettingsMessageReceivedListener onServerSettingsMessageReceivedListener;
    private volatile OnServerStateMessageReceivedListener onServerStateMessageReceivedListener;

    public ReceiverThread(Socket clientSocket, OnThreadFinishedListener onThreadFinishedListener) {
        this.clientSocket = clientSocket;
        this.onThreadFinishedListener = onThreadFinishedListener;
    }

    @Override
    public void run() {
        super.run();

        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "ReceiverThread is starting!");

        try {
            BufferedInputStream socketInput = new BufferedInputStream(clientSocket.getInputStream());
            outer:
            while (!Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < START_SEQUENCE.length; i++) {
                    int nextByte = socketInput.read();
                    if (nextByte < 0) {
                        // The stream is closed!
                        break outer;
                    }
                    if (nextByte != START_SEQUENCE[i]) {
                        continue outer;
                    }
                }

                int startCode = socketInput.read();

                ByteBuffer messageSizeBB = ByteBuffer.allocate(4);
                messageSizeBB.put((byte) socketInput.read());
                messageSizeBB.put((byte) socketInput.read());
                messageSizeBB.put((byte) socketInput.read());
                messageSizeBB.put((byte) socketInput.read());
                messageSizeBB.rewind();
                int messageSize = messageSizeBB.getInt();

                if (messageSize < 0 || messageSize >= MAX_MESSAGE_SIZE) {
                    GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.ERROR, "Invalid message size of " + messageSize + "!");
                    continue outer;
                }

                byte[] messageBytes = new byte[messageSize];
                for (int i = 0; i < messageBytes.length; i++) {
                    int nextByte = socketInput.read();
                    if (nextByte < 0) {
                        // The stream is closed!
                        break outer;
                    }
                    messageBytes[i] = (byte) nextByte;
                }

                if (startCode == new JPEGFrameMessage().getStartCode()) {
                    JPEGFrameMessage message = new JPEGFrameMessage().fromBytes(messageBytes);
                    if (onFrameReceivedListener != null)
                        onFrameReceivedListener.OnFrameReceived(message.getImage());
                } else if (startCode == new MotorStateMessage().getStartCode()) {
                    MotorStateMessage message = new MotorStateMessage().fromBytes(messageBytes);
                    if (onMotorStateMessageReceivedListener != null)
                        onMotorStateMessageReceivedListener.OnMotorStateMessageReceived(message);
                } else if (startCode == new ServerSettingsMessage().getStartCode()) {
                    ServerSettingsMessage message = new ServerSettingsMessage().fromBytes(messageBytes);
                    if (onServerSettingsMessageReceivedListener != null)
                        onServerSettingsMessageReceivedListener.OnServerSettingsMessageReceived(message);
                } else if (startCode == new ServerStateMessage().getStartCode()) {
                    ServerStateMessage message = new ServerStateMessage().fromBytes(messageBytes);
                    if (onServerStateMessageReceivedListener != null)
                        onServerStateMessageReceivedListener.OnServerStateMessageReceived(message);
                } else {
                    GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.ERROR, "Received illegal start code of " + startCode);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.WARNING, "ReceiverThread is finishing!");
            onThreadFinishedListener.onThreadFinished();
        }
    }

    public void setOnServerStateMessageReceivedListener(OnServerStateMessageReceivedListener onServerStateMessageReceivedListener) {
        this.onServerStateMessageReceivedListener = onServerStateMessageReceivedListener;
    }

    public void setOnFrameReceivedListener(OnFrameReceivedListener onFrameReceivedListener) {
        this.onFrameReceivedListener = onFrameReceivedListener;
    }

    public void setOnLoggableEventListener(OnLoggableEventListener onLoggableEventListener) {
        this.onLoggableEventListener = onLoggableEventListener;
    }

    public void setOnMotorStateMessageReceivedListener(OnMotorStateMessageReceivedListener onMotorStateMessageReceivedListener) {
        this.onMotorStateMessageReceivedListener = onMotorStateMessageReceivedListener;
    }

    public void setOnServerSettingsMessageReceivedListener(OnServerSettingsMessageReceivedListener onServerSettingsMessageReceivedListener) {
        this.onServerSettingsMessageReceivedListener = onServerSettingsMessageReceivedListener;
    }
}