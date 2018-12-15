package xyz.philiprodriguez.projectrovercommunications;

import android.graphics.Bitmap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectRoverServer {
    public static final String CLASS_IDENTIFIER = "ProjectRoverServer";
    public static final int MAX_MESSAGE_SIZE = 1024*1024*64; // 64MB
    public static final byte[] START_SEQUENCE = new byte[]{127, 65, 27, 94, 56, 23, 19, 122, 12, 56, 32, 49};

    private final int port;

    private final ServerSettings serverSettings;

    private final AtomicBoolean isKilled;

    private volatile ServerSocket serverSocket;
    private final Thread connectorThread;
    private volatile Thread inThread;
    private volatile Thread outThread;

    private volatile Socket clientSocket;
    private final AtomicBoolean isClientConnected;
    private final BlockingQueue<ByteableMessage> sendQueue = new LinkedBlockingQueue<ByteableMessage>();

    private volatile OnMotorStateMessageReceivedListener onMotorStateMessageReceivedListener;
    private volatile OnLoggableEventListener onLoggableEventListener;
    private volatile OnServerSettingsMessageReceivedListener onServerSettingsMessageReceivedListener;

    public ProjectRoverServer(final int port, final ServerSettings serverSettings) {
        this.port = port;
        this.serverSettings = serverSettings;
        this.isKilled = new AtomicBoolean(false);
        this.isClientConnected = new AtomicBoolean(false);

        connectorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                GlobalLogger.log(CLASS_IDENTIFIER, null, "Server connectorThread starting...");
                try {
                    serverSocket = new ServerSocket(port);

                    while (!Thread.currentThread().isInterrupted()) {
                        isClientConnected.set(false);

                        // Accept client
                        GlobalLogger.log(CLASS_IDENTIFIER, null, "Server connectorThread listening on port " + port + "...");
                        Socket cs = serverSocket.accept();
                        GlobalLogger.log(CLASS_IDENTIFIER, null, "Accepted client on socket " + getClientSocket());
                        setClientSocket(cs);


                        setInThread(new Thread(new Runnable() {
                            @Override
                            public void run() {
                                GlobalLogger.log(CLASS_IDENTIFIER, null, "Server inThread is starting!");
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
                                        GlobalLogger.log(CLASS_IDENTIFIER, "inThread", "Start sequence OK...");

                                        int startCode = socketInput.read();
                                        GlobalLogger.log(CLASS_IDENTIFIER, "inThread", "Start code of " + startCode);

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

                                        GlobalLogger.log(CLASS_IDENTIFIER, "inThread", "Message bytes read...");
                                        if (startCode == new MotorStateMessage().getStartCode()) {
                                            MotorStateMessage message = new MotorStateMessage().fromBytes(messageBytes);
                                            onMotorStateMessageReceivedListener.OnMotorStateMessageReceived(message);
                                        } else if (startCode == new ServerSettingsMessage().getStartCode()) {
                                            ServerSettingsMessage message = new ServerSettingsMessage().fromBytes(messageBytes);
                                            serverSettings.setFromServerSettings(message.getServerSettings());
                                            onServerSettingsMessageReceivedListener.OnServerSettingsMessageReceived(message);
                                        } else {
                                            GlobalLogger.log(CLASS_IDENTIFIER, "e", "Read illegal start code of " + startCode);
                                        }
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    GlobalLogger.log(CLASS_IDENTIFIER, "inThread", "Server inThread is exiting!");
                                    killClientConnection();
                                }
                            }
                        }));

                        setOutThread(new Thread(new Runnable() {
                            @Override
                            public void run() {
                                GlobalLogger.log(CLASS_IDENTIFIER, null, "Server outThread is starting!");
                                try {
                                    BufferedOutputStream socketOutput = new BufferedOutputStream(getClientSocket().getOutputStream());

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
                                    GlobalLogger.log(CLASS_IDENTIFIER, null, "Server outThread is exiting!");
                                    killClientConnection();
                                }
                            }
                        }));

                        getInThread().start();
                        getOutThread().start();
                        isClientConnected.set(true);

                        // Send out an initial ServerSettings message to let the client know where we are at!
                        sendQueue.add(new ServerSettingsMessage(System.currentTimeMillis(), serverSettings));

                        waitForKillClientConnection();
                    }
                } catch (SocketException e) {
                    GlobalLogger.log(CLASS_IDENTIFIER, null, "Socket exception occurred...");
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new IllegalStateException("Failed to start server!", e);
                } finally {
                    GlobalLogger.log(CLASS_IDENTIFIER, null, "Server connectorThread is exiting!");
                    killServer();
                }
            }
        });

        connectorThread.start();
    }

    public void killClientConnection() {
        GlobalLogger.log(CLASS_IDENTIFIER, null, "Killing client connection...");
        if (getInThread() != null)
            getInThread().interrupt();
        if (getOutThread() != null)
            getOutThread().interrupt();
        try {
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void waitForKillClientConnection() {
        GlobalLogger.log(CLASS_IDENTIFIER, null, "waitForKillClientConnection called!");
        try {
            GlobalLogger.log(CLASS_IDENTIFIER, null, "Getting inThread");
            Thread inThread = getInThread();
            GlobalLogger.log(CLASS_IDENTIFIER, null, "Getting outThread");
            Thread outThread = getOutThread();
            GlobalLogger.log(CLASS_IDENTIFIER, null, "Joining inThread");
            if (inThread != null)
                inThread.join();
            GlobalLogger.log(CLASS_IDENTIFIER, null, "Joining outThread");
            if (outThread != null)
                outThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            GlobalLogger.log(CLASS_IDENTIFIER, null, "waitForKillClientConnection interrupted!");
        } finally {
            GlobalLogger.log(CLASS_IDENTIFIER, null, "waitForKillClientConnection completed!");
        }
    }

    public void killServer() {
        GlobalLogger.log(CLASS_IDENTIFIER, null, "Killing server...");
        killClientConnection();
        connectorThread.interrupt();
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        isKilled.set(true);
    }
    public void waitForKillServer() {
        try {
            waitForKillClientConnection();
            connectorThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new IllegalStateException("Failed to kill server!", e);
        } finally {
            GlobalLogger.log(CLASS_IDENTIFIER, null, "waitForKillServer completed!");
        }
    }

    public synchronized boolean isKilled() {
        return isKilled.get();
    }

    private synchronized void setClientSocket(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    private synchronized Socket getClientSocket() {
        return clientSocket;
    }

    private synchronized void setInThread(Thread inThread) {
        this.inThread = inThread;
    }

    private synchronized Thread getInThread() {
        return inThread;
    }

    private synchronized void setOutThread(Thread outThread) {
        this.outThread = outThread;
    }

    private synchronized Thread getOutThread() {
        return outThread;
    }

    public synchronized void doEnqueueImageAndRecycleBitmap(Bitmap bitmap) {
        if (!isKilled() && isClientConnected.get()) {
            JPEGFrameMessage jpegFrameMessage = new JPEGFrameMessage(System.currentTimeMillis(), bitmap, serverSettings.getJpegQuality());
            sendQueue.add(jpegFrameMessage);
            bitmap.recycle();
        }
    }

    public synchronized void doEnqueueServerStateMessage(ServerStateMessage serverStateMessage) {
        sendQueue.add(serverStateMessage);
    }

    public synchronized void setOnMotorStateMessageReceivedListener(OnMotorStateMessageReceivedListener onMotorStateMessageReceivedListener) {
        this.onMotorStateMessageReceivedListener = onMotorStateMessageReceivedListener;
    }

    public synchronized void setOnLoggableEventListener(OnLoggableEventListener onLoggableEventListener) {
        this.onLoggableEventListener = onLoggableEventListener;
    }

    public synchronized void setOnServerSettingsMessageReceivedListener(OnServerSettingsMessageReceivedListener onServerSettingsMessageReceivedListener) {
        this.onServerSettingsMessageReceivedListener = onServerSettingsMessageReceivedListener;
    }

    public ServerSettings getServerSettings() {
        return serverSettings;
    }
}
