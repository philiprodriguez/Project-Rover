package xyz.philiprodriguez.projectrovercommunications;

import android.graphics.Bitmap;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectRoverServer {
    // A class identifier just for logging purposes.
    public static final String CLASS_IDENTIFIER = "ProjectRoverServer";

    // This is the port on which the server is running.
    private final int port;

    // Listeners to be attached to the underlying receiverThread once it is started
    private volatile OnMotorStateMessageReceivedListener onMotorStateMessageReceivedListener;
    private volatile OnLoggableEventListener onLoggableEventListener;
    private volatile OnServerSettingsMessageReceivedListener onServerSettingsMessageReceivedListener;

    // This is the container for the server's current client-mutable settings.
    private final ServerSettings serverSettings;

    // This represents whether or not the server is in the killed state.
    private final AtomicBoolean isKilled;

    private volatile ServerSocket serverSocket;
    private final Thread connectorThread;
    private volatile ReceiverThread receiverThread;
    private volatile SenderThread senderThread;

    private volatile Socket clientSocket;
    private final AtomicBoolean isClientConnected;

    public ProjectRoverServer(final int port, final ServerSettings serverSettings) {
        this.port = port;
        this.serverSettings = serverSettings;
        this.isKilled = new AtomicBoolean(false);
        this.isClientConnected = new AtomicBoolean(false);

        connectorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Server connectorThread starting...");
                try {
                    serverSocket = new ServerSocket(port);

                    while (!Thread.currentThread().isInterrupted()) {
                        isClientConnected.set(false);

                        // Accept client
                        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Server connectorThread listening on port " + port + "...");
                        Socket cs = serverSocket.accept();
                        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Accepted client on socket " + clientSocket);
                        clientSocket = cs;


                        receiverThread = new ReceiverThread(clientSocket, new OnThreadFinishedListener() {
                            @Override
                            public void onThreadFinished() {
                                killClientConnection();
                            }
                        });
                        receiverThread.setOnServerSettingsMessageReceivedListener(new OnServerSettingsMessageReceivedListener() {
                            @Override
                            public void OnServerSettingsMessageReceived(ServerSettingsMessage serverSettingsMessage) {
                                // Update our ServerSettings
                                serverSettings.setFromServerSettings(serverSettingsMessage.getServerSettings());

                                // Pass through
                                if (onServerSettingsMessageReceivedListener != null) {
                                    onServerSettingsMessageReceivedListener.OnServerSettingsMessageReceived(serverSettingsMessage);
                                }
                            }
                        });
                        receiverThread.setOnLoggableEventListener(onLoggableEventListener);
                        receiverThread.setOnMotorStateMessageReceivedListener(onMotorStateMessageReceivedListener);

                        senderThread = new SenderThread(clientSocket, new OnThreadFinishedListener() {
                            @Override
                            public void onThreadFinished() {
                                killClientConnection();
                            }
                        }, 10);

                        receiverThread.start();
                        senderThread.start();
                        isClientConnected.set(true);

                        // Send out an initial ServerSettings message to let the client know where we are at!
                        senderThread.enqueueStrict(new ServerSettingsMessage(System.currentTimeMillis(), serverSettings));

                        waitForKillClientConnection();
                    }
                } catch (SocketException e) {
                    GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.ERROR, "Socket exception occurred...");
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new IllegalStateException("Failed to start server!", e);
                } finally {
                    GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.ERROR, "Server connectorThread is exiting!");
                    killServer();
                }
            }
        });

        connectorThread.start();
    }

    public void killClientConnection() {
        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Killing client connection...");
        if (receiverThread != null)
            receiverThread.interrupt();
        if (senderThread != null)
            senderThread.interrupt();
        try {
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void waitForKillClientConnection() {
        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "waitForKillClientConnection called!");
        try {
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.EXCESS, "Joining receiverThread");
            if (receiverThread != null)
                receiverThread.join();
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.EXCESS, "Joining senderThread");
            if (senderThread != null)
                senderThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.EXCESS, "waitForKillClientConnection interrupted!");
        } finally {
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "waitForKillClientConnection completed!");
        }
    }

    public void killServer() {
        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Killing server...");
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
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "waitForKillServer completed!");
        }
    }

    public synchronized boolean isKilled() {
        return isKilled.get();
    }

    public synchronized void doEnqueueImageAndRecycleBitmap(Bitmap bitmap) {
        if (!isKilled() && isClientConnected.get()) {
            JPEGFrameMessage jpegFrameMessage = new JPEGFrameMessage(System.currentTimeMillis(), bitmap, serverSettings.getJpegQuality());
            senderThread.enqueueDroppable(jpegFrameMessage);
            bitmap.recycle();
        }
    }

    public synchronized void doEnqueueServerStateMessage(ServerStateMessage serverStateMessage) {
        if (!isKilled() && isClientConnected.get()) {
            senderThread.enqueueStrict(serverStateMessage);
        }
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
