package xyz.philiprodriguez.projectrovercommunications;

import java.nio.ByteBuffer;
import java.util.Date;

// Message containing one "frame" of audio 8-bit PCM data
public class PCMFrameMessage implements ByteableMessage<PCMFrameMessage>{
    private final long timestamp;
    private final byte[] frameBytes;

    public PCMFrameMessage() {
        this(-1, null);
    }

    public PCMFrameMessage(long timestamp, byte[] frameBytes) {
        this.timestamp = timestamp;
        this.frameBytes = frameBytes;
    }

    public byte[] getFrameBytes() {
        return frameBytes;
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
    public PCMFrameMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long timestamp = byteBuffer.getLong();
        byte[] frameBytes = new byte[messageBytes.length - 8];
        for (int i = 0; i < frameBytes.length; i++) {
            frameBytes[i] = byteBuffer.get();
        }
        return new PCMFrameMessage(timestamp, frameBytes);
    }

    @Override
    public byte getStartCode() {
        return 44;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String toString() {
        return "PCMFrameMessage of size " + frameBytes.length + " and time " + getTimestamp();
    }
}
