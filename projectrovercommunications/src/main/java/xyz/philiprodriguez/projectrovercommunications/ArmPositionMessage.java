package xyz.philiprodriguez.projectrovercommunications;

import java.nio.ByteBuffer;

public class ArmPositionMessage implements ByteableMessage<ArmPositionMessage> {

    private final long timestamp;

    // in meters
    private final double x;
    private final double y;
    private final double z;

    public ArmPositionMessage() {
        this(-1, -1, -1, -1);
    }

    public ArmPositionMessage(long timestamp, double x, double y, double z) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public byte[] getBytes() {
        int extraLength = 8 + 8*3;

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

        // Bytes
        byteBuffer.putDouble(x);
        byteBuffer.putDouble(y);
        byteBuffer.putDouble(z);


        return byteBuffer.array();
    }

    @Override
    public ArmPositionMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long ts = byteBuffer.getLong();
        double xv = byteBuffer.getDouble();
        double yv = byteBuffer.getDouble();
        double zv = byteBuffer.getDouble();
        return new ArmPositionMessage(ts, xv, yv, zv);
    }

    @Override
    public byte getStartCode() {
        return 49;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
