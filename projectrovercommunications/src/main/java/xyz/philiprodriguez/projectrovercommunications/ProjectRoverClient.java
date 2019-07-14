package xyz.philiprodriguez.projectrovercommunications;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class should handle, to the greatest extent possible, all backend logic stuff for the client
 * of Project Rover. It should certainly handle, for instance, the connection of a socket to the
 * server and provide easy ways for sending and handling the reception of messages.
 */
public class ProjectRoverClient {
    // The class identifier, just for logging purposes.
    public static final String CLASS_IDENTIFIER = "ProjectRoverClient";

    // The address and port of the server this client was constructed to connect to
    private final String address;
    private final int port;

    // The socket with which this client connected to the server.
    private final Socket clientSocket;

    private volatile OnClientConnectionKilledListener onClientConnectionKilledListener;

    // Threads for reading in data from the socket and writing message bytes to the socket.
    private final ReceiverThread receiverThread;
    private final SenderThread senderThread;

    // A boolean representing whether or not this client has been "killed". Killed, in this case,
    // means that both the inThread and outThread have been interrupted, and the client socket has
    // been closed.
    private final AtomicBoolean isKilled = new AtomicBoolean(false);

    // This object represents the perceived settings the server is using. When the client connection
    // is initially opened, the server should send over a ServerSettingsMessage to tell the client
    // the starting ServerSettings, and that will be stored here.
    private final ServerSettings perceivedServerSettings;

    // This object holds the perceived state information of the server. This is stuff that is
    // different from server settings. This is stuff like battery life of the robot, and perhaps
    // sensor information of the robot, etc.
    private volatile ServerStateMessage latestServerStateMessage;


    /**
     * This not only constructs the object but also does the following:
     *
     * + Initializes the perceived ServerSettings to a default object, meaning that until the server
     * corrects us, the client assumes that the server was just opened.
     *
     * + Creates the socket and connects it to the server using the provided address and port. If
     * connection fails, an IOException is thrown and thus the client object is finished.
     *
     * + Initializes and runs the receiverThread.
     *
     * + Initializes and runs the senderThread.
     *
     * @param address The IPv4 address of the server (robot).
     * @param port The port the server is running on.
     * @throws IOException If the client fails to connect the socket to the server.
     */
    public ProjectRoverClient(String address, int port) throws IOException {
        this.address = address;
        this.port = port;
        this.perceivedServerSettings = new ServerSettings();

        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Attempting to connect to server...");
        this.clientSocket = new Socket(address, port);
        this.clientSocket.setSoTimeout(5000);

        receiverThread = new ReceiverThread(clientSocket, new OnThreadFinishedListener() {
            @Override
            public void onThreadFinished() {
                killClientConnection();
            }
        });
        receiverThread.setOnServerSettingsMessageReceivedListener(new OnServerSettingsMessageReceivedListener() {
            @Override
            public void OnServerSettingsMessageReceived(ServerSettingsMessage serverSettingsMessage) {
                perceivedServerSettings.setFromServerSettings(serverSettingsMessage.getServerSettings());
            }
        });

        senderThread = new SenderThread(clientSocket, new OnThreadFinishedListener() {
            @Override
            public void onThreadFinished() {
                killClientConnection();
            }
        }, 10);

        senderThread.start();
        receiverThread.start();
    }

    /**
     *  This method calls close on the client socket and interrupts both the receiverThread and the
     *  senderThread. After doing this, this method sets isKilled to true and, if appropriate, calls
     *  the OnClientConnectionKilled callback.
     */
    public void killClientConnection() {
        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Killing client connection...");
        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }
        if (!isKilled.getAndSet(true)) {
            if (onClientConnectionKilledListener != null) {
                onClientConnectionKilledListener.OnClientConnectionKilled();
            }
        }
    }

    /**
     * This method reports whether or not the client object is in the killed state. The client
     * is in the killed state only after killClientConnection has been called.
     *
     * @return whether the client object is in the killed state.
     */
    public boolean isKilled() {
        return isKilled.get();
    }

    /**
     * This method joins on both the receiverThread and senderThread, and therefore waits for the
     * client connection to finish being placed into the killed state.
     */
    public void waitForKillClientConnection() {
        try {
            if (receiverThread != null)
                receiverThread.join();
            if (senderThread != null)
                senderThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.WARNING, "waitForKillClientConnection interrupted!");
        } finally {
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.EXCESS, "waitForKillClientConnection completed!");
        }
    }

    public void setOnFrameReceivedListener(OnFrameReceivedListener onFrameReceivedListener) {
        this.receiverThread.setOnFrameReceivedListener(onFrameReceivedListener);
    }

    public void setOnClientConnectionKilledListener(OnClientConnectionKilledListener onClientConnectionKilledListener) {
        this.onClientConnectionKilledListener = onClientConnectionKilledListener;
    }

    public void setOnServerStateMessageReceivedListener(OnServerStateMessageReceivedListener onServerStateMessageReceivedListener) {
        this.receiverThread.setOnServerStateMessageReceivedListener(onServerStateMessageReceivedListener);
    }

    public void doEnqueueMotorStateMessage(MotorStateMessage motorStateMessage) {
        senderThread.enqueueStrict(motorStateMessage);
    }

    public void doEnqueueServerSettingsMessage(ServerSettingsMessage serverSettingsMessage) {
        senderThread.enqueueStrict(serverSettingsMessage);
    }

    public void doEnqueueArmPositionMessage(ArmPositionMessage armPositionMessage) {
        senderThread.enqueueStrict(armPositionMessage);
    }

    public ServerSettings getPerceivedServerSettings() {
        return perceivedServerSettings;
    }

    public ServerStateMessage getLatestServerStateMessage() {
        return latestServerStateMessage;
    }
}
