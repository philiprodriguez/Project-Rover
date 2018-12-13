package xyz.philiprodriguez.projectrovercommunications;

import android.support.annotation.NonNull;

public class ServerSettings {
    private volatile int headlightBrightness;
    private volatile boolean headlightOn;
    private volatile int servoRotationAmount;
    private volatile int jpegQuality;

    public ServerSettings() {
        // Initialize default values
        this.headlightBrightness = 0;
        this.jpegQuality = 30;
        this.headlightOn = false;
        this.servoRotationAmount = 0;
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

    public synchronized boolean getHeadlightOn() {
        return headlightOn;
    }

    public synchronized void setHeadlightOn(boolean headlightOn) {
        this.headlightOn = headlightOn;
    }

    public synchronized int getServoRotationAmount() {
        return servoRotationAmount;
    }

    public synchronized void setServoRotationAmount(int servoRotationAmount) {
        this.servoRotationAmount = servoRotationAmount;
    }

    @NonNull
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("headlightBrightness = ");
        sb.append(getHeadlightBrightness());
        sb.append(System.lineSeparator());
        sb.append("headlightOn = ");
        sb.append(getHeadlightOn());
        sb.append(System.lineSeparator());
        sb.append("servoRotationAmount = ");
        sb.append(getServoRotationAmount());
        sb.append(System.lineSeparator());
        sb.append("jpegQuality = ");
        sb.append(getJpegQuality());
        return sb.toString();
    }
}
