package xyz.philiprodriguez.projectrovercommunications;

import android.support.annotation.NonNull;

public class ServerSettings {
    private volatile int headlightBrightness;
    private volatile int jpegQuality;

    public ServerSettings() {
        // Initialize default values
        this.headlightBrightness = 0;
        this.jpegQuality = 70;
    }

    public synchronized void setHeadlightBrightness(int headlightBrightness) {
        this.headlightBrightness = headlightBrightness;
    }

    public synchronized void setJpegQuality(int jpegQuality) {
        this.jpegQuality = jpegQuality;
    }

    public synchronized int getHeadlightBrightness() {
        return headlightBrightness;
    }

    public synchronized int getJpegQuality() {
        return jpegQuality;
    }

    @NonNull
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("headlightBrightness = ");
        sb.append(getHeadlightBrightness());
        sb.append(System.lineSeparator());
        sb.append("jpegQuality = ");
        sb.append(getJpegQuality());
        return sb.toString();
    }
}
