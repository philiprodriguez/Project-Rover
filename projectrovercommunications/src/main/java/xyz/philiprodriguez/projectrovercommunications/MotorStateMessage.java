package xyz.philiprodriguez.projectrovercommunications;

import java.nio.ByteBuffer;

public class MotorStateMessage implements ByteableMessage<MotorStateMessage> {

    private final long timestamp;
    private final int leftForward;
    private final int leftBackward;
    private final int rightForward;
    private final int rightBackward;

    public MotorStateMessage() {
        this(-1, -1, -1, -1, -1);
    }

    public MotorStateMessage(long timestamp, int leftForward, int leftBackward, int rightForward, int rightBackward) {
        this.timestamp = timestamp;
        this.leftForward = leftForward;
        this.leftBackward = leftBackward;
        this.rightForward = rightForward;
        this.rightBackward = rightBackward;
    }

    public int getLeftBackward() {
        return leftBackward;
    }

    public int getLeftForward() {
        return leftForward;
    }

    public int getRightBackward() {
        return rightBackward;
    }

    public int getRightForward() {
        return rightForward;
    }

    @Override
    public byte[] getBytes() {
        int extraLength = 8 + 4*4;

        ByteBuffer byteBuffer = ByteBuffer.allocate(ProjectRoverServer.START_SEQUENCE.length + 1 + 4 + extraLength);

        // Start sequence
        for (int i = 0; i < ProjectRoverServer.START_SEQUENCE.length; i++) {
            byteBuffer.put(ProjectRoverServer.START_SEQUENCE[i]);
        }

        // Start code
        byteBuffer.put(getStartCode());

        // Message length
        byteBuffer.putInt(extraLength);

        // timestamp
        byteBuffer.putLong(getTimestamp());

        // Bytes
        byteBuffer.putInt(leftForward);
        byteBuffer.putInt(leftBackward);
        byteBuffer.putInt(rightForward);
        byteBuffer.putInt(rightBackward);


        return byteBuffer.array();
    }

    @Override
    public MotorStateMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long timestamp = byteBuffer.getLong();
        int lf = byteBuffer.getInt();
        int lb = byteBuffer.getInt();
        int rf = byteBuffer.getInt();
        int rb = byteBuffer.getInt();
        return new MotorStateMessage(timestamp, lf, lb, rf, rb);
    }

    @Override
    public byte getStartCode() {
        return 10;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return getLeftForward() + ", " + getLeftBackward() + ", " + getRightForward() + ", " + getRightBackward();
    }
}
