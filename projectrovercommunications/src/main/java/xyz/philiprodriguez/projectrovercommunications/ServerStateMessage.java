package xyz.philiprodriguez.projectrovercommunications;

import java.nio.ByteBuffer;

public class ServerStateMessage implements ByteableMessage<ServerStateMessage>{
    private final long timestamp;
    private final int phoneBatteryLevel;

    public ServerStateMessage() {
        this(-1, -1);
    }

    public ServerStateMessage(long timestamp, int phoneBatteryLevel) {
        this.timestamp = timestamp;
        this.phoneBatteryLevel = phoneBatteryLevel;
    }

    @Override
    public byte[] getBytes() {
        int extraLength = 8 + 4;

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

        // Payload
        byteBuffer.putInt(phoneBatteryLevel);


        return byteBuffer.array();
    }

    @Override
    public ServerStateMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long timestamp = byteBuffer.getLong();
        int pbl = byteBuffer.getInt();
        return new ServerStateMessage(timestamp, pbl);
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
}
