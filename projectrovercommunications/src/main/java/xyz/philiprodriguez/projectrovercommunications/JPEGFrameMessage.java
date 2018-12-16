package xyz.philiprodriguez.projectrovercommunications;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Date;

public class JPEGFrameMessage implements ByteableMessage<JPEGFrameMessage> {
    private final long timestamp;
    private final byte[] frameBytes;

    public JPEGFrameMessage() {
        this(-1, null);
    }

    public JPEGFrameMessage(long timestamp, byte[] frameBytes) {
        this.timestamp = timestamp;
        this.frameBytes = frameBytes;
    }

    public JPEGFrameMessage(long timestamp, Bitmap bitmap, int jpegQuality) {
        this.timestamp = timestamp;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, byteArrayOutputStream);
        this.frameBytes = byteArrayOutputStream.toByteArray();
    }

    public Bitmap getImage() {
        return BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.length);
    }

    @Override
    public byte[] getBytes() {
        int extraLength = 8 + frameBytes.length;

        ByteBuffer byteBuffer = ByteBuffer.allocate(ReceiverThread.START_SEQUENCE.length + 1 + 4 + extraLength);

        // Start sequence
        for (int i = 0; i < ReceiverThread.START_SEQUENCE.length; i++) {
            byteBuffer.put(ReceiverThread.START_SEQUENCE[i]);
        }

        // Start code
        byteBuffer.put(getStartCode());

        // Message length
        byteBuffer.putInt(extraLength);

        // timestamp
        byteBuffer.putLong(getTimestamp());

        // Frame bytes
        for (int i = 0; i < frameBytes.length; i++) {
            byteBuffer.put(frameBytes[i]);
        }

        return byteBuffer.array();
    }

    @Override
    public JPEGFrameMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long timestamp = byteBuffer.getLong();
        byte[] frameBytes = new byte[messageBytes.length - 8];
        for (int i = 0; i < frameBytes.length; i++) {
            frameBytes[i] = byteBuffer.get();
        }
        return new JPEGFrameMessage(timestamp, frameBytes);
    }

    @Override
    public byte getStartCode() {
        return 5;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String toString() {
        return "JPEGFrameMessage of size " + frameBytes.length + " and time " + new Date(getTimestamp());
    }
}
