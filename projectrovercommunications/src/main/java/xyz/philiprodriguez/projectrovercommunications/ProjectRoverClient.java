package xyz.philiprodriguez.projectrovercommunications;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static xyz.philiprodriguez.projectrovercommunications.ProjectRoverServer.MAX_MESSAGE_SIZE;
import static xyz.philiprodriguez.projectrovercommunications.ProjectRoverServer.START_SEQUENCE;

public class ProjectRoverClient {
    public static final String CLASS_IDENTIFIER = "ProjectRoverClient";

    private final String address;
    private final int port;

    private volatile OnFrameReceivedListener onFrameReceivedListener;
    private volatile OnClientConnectionKilledListener onClientConnectionKilledListener;

    private final Socket clientSocket;

    private final Thread inThread;
    private final Thread outThread;
    private final AtomicBoolean isKilled = new AtomicBoolean(false);

    private final BlockingQueue<ByteableMessage> sendQueue = new LinkedBlockingQueue<ByteableMessage>();

    public ProjectRoverClient(String address, int port) throws IOException {
        this.address = address;
        this.port = port;

        GlobalLogger.log(CLASS_IDENTIFIER, null, "Attempting to connect to server...");
        this.clientSocket = new Socket(address, port);
        this.clientSocket.setSoTimeout(5000);

        inThread = new Thread(new Runnable() {
            @Override
            public void run() {
                GlobalLogger.log(CLASS_IDENTIFIER, null, "Client inThread is starting!");
                try {
                    BufferedInputStream socketInput = new BufferedInputStream(clientSocket.getInputStream());
                    outer: while (!Thread.currentThread().isInterrupted()) {
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
                            GlobalLogger.log(CLASS_IDENTIFIER, "e", "Invalid message size of " + messageSize + "!");
                            continue outer;
                        }
                        GlobalLogger.log(CLASS_IDENTIFIER, "inThread", "Message size of " + messageSize);

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
                            onFrameReceivedListener.OnFrameReceived(message.getImage());
                        } else {
                            GlobalLogger.log(CLASS_IDENTIFIER, "e", "Read illegal start code of " + startCode);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    GlobalLogger.log(CLASS_IDENTIFIER, "inThread", "Client inThread is exiting!");
                    killClientConnection();
                }
            }
        });

        outThread = new Thread(new Runnable() {
            @Override
            public void run() {
                GlobalLogger.log(CLASS_IDENTIFIER, null, "Client outThread is starting!");
                try {
                    BufferedOutputStream socketOutput = new BufferedOutputStream(clientSocket.getOutputStream());

                    while (!Thread.currentThread().isInterrupted()) {
                        ByteableMessage nextMessage = sendQueue.take();
                        GlobalLogger.log(CLASS_IDENTIFIER, null, "Sending message " + nextMessage.toString());
                        socketOutput.write(nextMessage.getBytes());
                        socketOutput.flush();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    GlobalLogger.log(CLASS_IDENTIFIER, null, "Client outThread is exiting!");
                    killClientConnection();
                }
            }
        });

        outThread.start();
        inThread.start();
    }

    public void killClientConnection() {
        GlobalLogger.log(CLASS_IDENTIFIER, null, "Killing client connection...");
        try {
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (inThread != null) {
            inThread.interrupt();
        }
        if (outThread != null) {
            outThread.interrupt();
        }
        if (!isKilled.getAndSet(true)) {
            onClientConnectionKilledListener.OnClientConnectionKilled();
        }
    }

    public boolean isKilled() {
        return isKilled.get();
    }

    public void waitForKillClientConnection() {
        try {
            if (inThread != null)
                inThread.join();
            if (outThread != null)
                outThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new IllegalStateException("Failed to wait for kill client connection!", e);
        } finally {
            GlobalLogger.log(CLASS_IDENTIFIER, null, "waitForKillClientConnection completed!");
        }
    }


    public synchronized OnFrameReceivedListener getOnFrameReceivedListener() {
        return onFrameReceivedListener;
    }

    public synchronized void setOnFrameReceivedListener(OnFrameReceivedListener onFrameReceivedListener) {
        this.onFrameReceivedListener = onFrameReceivedListener;
    }

    public synchronized void setOnClientConnectionKilledListener(OnClientConnectionKilledListener onClientConnectionKilledListener) {
        this.onClientConnectionKilledListener = onClientConnectionKilledListener;
    }

    public synchronized OnClientConnectionKilledListener getOnClientConnectionKilledListener() {
        return onClientConnectionKilledListener;
    }

    public synchronized void doEnqueueMotorStateMessage(MotorStateMessage motorStateMessage) {
        sendQueue.add(motorStateMessage);
    }
}
