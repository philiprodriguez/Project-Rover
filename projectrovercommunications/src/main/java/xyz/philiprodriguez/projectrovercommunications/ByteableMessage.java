package xyz.philiprodriguez.projectrovercommunications;

public interface ByteableMessage<T> {
    byte[] getBytes();
    T fromBytes(byte[] messageBytes);
    byte getStartCode();
    long getTimestamp();
}
