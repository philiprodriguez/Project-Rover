package xyz.philiprodriguez.projectrovercommunications;

import java.nio.ByteBuffer;
import java.util.Date;

// Message containing one "frame" of audio 8-bit PCM data
public class PCMFrameMessage implements ByteableMessage<PCMFrameMessage>{
    private final long timestamp;
    private final short[] pcmValues;

    public PCMFrameMessage() {
        this(-1, null);
    }

    public PCMFrameMessage(long timestamp, short[] pcmValues) {
        this.timestamp = timestamp;
        this.pcmValues = pcmValues;
    }

    public short[] getPCMValues() {
        return pcmValues;
    }

    @Override
    public byte[] getBytes() {
        int extraLength = 8 + 2*pcmValues.length;

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
        for (int i = 0; i < pcmValues.length; i++) {
            byteBuffer.putShort(pcmValues[i]);
        }

        return byteBuffer.array();
    }

    @Override
    public PCMFrameMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long timestamp = byteBuffer.getLong();
        short[] pcmValues = new short[(messageBytes.length - 8)/2];
        for (int i = 0; i < pcmValues.length; i++) {
            pcmValues[i] = byteBuffer.getShort();
        }
        return new PCMFrameMessage(timestamp, pcmValues);
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
        return "PCMFrameMessage of size " + pcmValues.length + " and time " + getTimestamp();
    }
}
