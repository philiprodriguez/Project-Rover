package xyz.philiprodriguez.projectrovercommunications;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

public class SenderThread extends Thread {
    public static final String CLASS_IDENTIFIER = "SenderThread";

    private final Socket clientSocket;

    private final OnThreadFinishedListener onThreadFinishedListener;

    private final LinkedBlockingQueue<ByteableMessage> sendQueue;

    public SenderThread(Socket clientSocket, OnThreadFinishedListener onThreadFinishedListener) {
        this.clientSocket = clientSocket;
        this.onThreadFinishedListener = onThreadFinishedListener;
        this.sendQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void run() {
        super.run();

        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "SenderThread is starting!");
        try {
            BufferedOutputStream socketOutput = new BufferedOutputStream(clientSocket.getOutputStream());

            while (!Thread.currentThread().isInterrupted()) {
                ByteableMessage nextMessage = sendQueue.take();
                GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Sending message " + nextMessage.toString());
                socketOutput.write(nextMessage.getBytes());
                socketOutput.flush();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.WARNING, "SenderThread is finishing!");
            onThreadFinishedListener.onThreadFinished();
        }
    }

    public LinkedBlockingQueue<ByteableMessage> getSendQueue() {
        return sendQueue;
    }
}
