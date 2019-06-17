package xyz.philiprodriguez.projectrovercommunications;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

public class SenderThread extends Thread {

    public static final String CLASS_IDENTIFIER = "SenderThread";

    private final Socket clientSocket;

    private final OnThreadFinishedListener onThreadFinishedListener;

    // This object simply exists as a way for this thread to wait for a message to be enqueued into
    // one of the queues.Additionally it serves as a way to ensure that the queues cannot be
    // modified at the same time.
    private final Object queueModificationLockAndMonitor = new Object();

    private final LinkedList<ByteableMessage> strictSendQueue;

    private final int droppableQueueSizeLimit;

    private final LinkedList<ByteableMessage> droppableSendQueue;

    public SenderThread(Socket clientSocket, OnThreadFinishedListener onThreadFinishedListener, int droppableQueueSizeLimit) {
        this.clientSocket = clientSocket;
        this.onThreadFinishedListener = onThreadFinishedListener;
        this.droppableQueueSizeLimit = droppableQueueSizeLimit;
        this.strictSendQueue = new LinkedList<>();
        this.droppableSendQueue = new LinkedList<>();
    }

    @Override
    public void run() {
        super.run();

        GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "SenderThread is starting!");
        try {
            BufferedOutputStream socketOutput = new BufferedOutputStream(clientSocket.getOutputStream());

            while (!Thread.currentThread().isInterrupted()) {
                synchronized (queueModificationLockAndMonitor) {
                    // Are there no messages? If so, wait for one.
                    if (strictSendQueue.size() <= 0 && droppableSendQueue.size() <= 0) {
                        queueModificationLockAndMonitor.wait();
                    }

                    // At this point, we should have some message in one of the queues.

                    // Check strict message
                    if (strictSendQueue.size() > 0) {
                        socketOutput.write(strictSendQueue.pollFirst().getBytes());
                    }

                    // Check droppable message
                    if (droppableSendQueue.size() > 0) {
                        socketOutput.write(droppableSendQueue.pollFirst().getBytes());
                    }

                    socketOutput.flush();
                }
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

//    public LinkedBlockingQueue<ByteableMessage> getSendQueue() {
//        return sendQueue;
//    }

    /**
     *  When using this method, the message is guaranteed to eventually be sent unless the socket
     *  closes.
     *
     * @param message The message to be enqueued for sending.
     */
    public void enqueueStrict(ByteableMessage message) {
        synchronized (queueModificationLockAndMonitor) {
            strictSendQueue.addLast(message);
            queueModificationLockAndMonitor.notify();
        }
    }

    /**
     * When using this method, the message is not guaranteed to eventually be sent, since it may get
     * evicted if the queue fills up too quickly.
     *
     * @param message The message to be enqueued for sending.
     */
    public void enqueueDroppable(ByteableMessage message) {
        synchronized (queueModificationLockAndMonitor) {
            // Drop old if limit exceeded
            int numDropped = 0;
            while (droppableSendQueue.size() >= droppableQueueSizeLimit) {
                droppableSendQueue.pollFirst();
                numDropped++;
            }
            if (numDropped > 0)
                GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.WARNING, "Dropped " + numDropped + " messages!");

            droppableSendQueue.addLast(message);
            queueModificationLockAndMonitor.notify();
        }
    }
}
