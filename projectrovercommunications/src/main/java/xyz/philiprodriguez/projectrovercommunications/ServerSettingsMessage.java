package xyz.philiprodriguez.projectrovercommunications;

import java.nio.ByteBuffer;

public class ServerSettingsMessage implements ByteableMessage<ServerSettingsMessage> {

    private final long timestamp;
    private final ServerSettings serverSettings;

    public ServerSettingsMessage() {
        this(-1, null);
    }

    public ServerSettingsMessage(long timestamp, ServerSettings serverSettings) {
        this.serverSettings = serverSettings;
        this.timestamp = timestamp;
    }

    @Override
    public byte[] getBytes() {
        int extraLength = 8 + (1 + 4 + 4);

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

        // Server settings bytes
        byteBuffer.put(serverSettings.getHeadlightOn() ? (byte)1 : (byte)0);
        byteBuffer.putInt(serverSettings.getServoRotationAmount());
        byteBuffer.putInt(serverSettings.getJpegQuality());

        return byteBuffer.array();
    }

    @Override
    public ServerSettingsMessage fromBytes(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        long timestamp = byteBuffer.getLong();
        ServerSettings ret = new ServerSettings();
        ret.setHeadlightOn(byteBuffer.get() == (byte)1);
        ret.setServoRotationAmount(byteBuffer.getInt());
        ret.setJpegQuality(byteBuffer.getInt());
        return new ServerSettingsMessage(timestamp, ret);
    }

    @Override
    public byte getStartCode() {
        return 112;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public ServerSettings getServerSettings() {
        return serverSettings;
    }
}
