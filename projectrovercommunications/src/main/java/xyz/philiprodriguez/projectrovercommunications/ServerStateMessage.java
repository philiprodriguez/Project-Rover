package xyz.philiprodriguez.projectrovercommunications;

import java.nio.ByteBuffer;

public class ServerStateMessage implements ByteableMessage<ServerStateMessage>{
    private final long timestamp;
    private final int phoneBatteryLevel;
    private final int primaryBatteryLevel;

    public ServerStateMessage() {
        this(-1, -1, -1);
    }

    public ServerStateMessage(long timestamp, int phoneBatteryLevel, int primaryBatteryLevel) {
        this.timestamp = timestamp;
        this.phoneBatteryLevel = phoneBatteryLevel;
        this.primaryBatteryLevel = primaryBatteryLevel;
    }

    @Override
    public byte[] getBytes() {
        int extraLength = 8 + 4 + 4;

        ByteBuffer byteBuffer = ByteBuffer.allocate(ReceiverThread.START_SEQUENCE.length + 1 + 4 + extraLength);

        // Start sequence
        for (int i = 0; i < ReceiverThread.START_SEQUENCE.length; i++) {
            byteBuffer.put(ReceiverThread.START_SEQUENCE[i]);
        }

        // Start code
        byteBuffer.put(getStartCode());

        // Message length
        byteBuffer.putInt(extraLength);

        // Payload
        byteBuffer.putLong(getTimestamp());
        byteBuffer.putInt(phoneBatteryLevel);
        byteBuffer.putInt(primaryBatteryLevel);

        return byteBuffer.array();
    }

    @Override
    public ServerStateMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long timestamp = byteBuffer.getLong();
        int phonebl = byteBuffer.getInt();
        int primarybl = byteBuffer.getInt();
        return new ServerStateMessage(timestamp, phonebl, primarybl);
    }

    @Override
    public byte getStartCode() {
        return 87;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public int getPhoneBatteryLevel() {
        return phoneBatteryLevel;
    }

    public int getPrimaryBatteryLevel() {
        return primaryBatteryLevel;
    }
}
