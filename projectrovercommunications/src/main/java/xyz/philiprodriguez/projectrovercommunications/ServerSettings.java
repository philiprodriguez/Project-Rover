package xyz.philiprodriguez.projectrovercommunications;

import android.support.annotation.NonNull;

public class ServerSettings {
    private volatile boolean headlightOn;
    private volatile int servoRotationAmount;
    private volatile int jpegQuality;

    public ServerSettings() {
        // Initialize default values
        this.jpegQuality = 30;
        this.headlightOn = false;
        this.servoRotationAmount = 0;
    }

    public synchronized void setJpegQuality(int jpegQuality) {
        this.jpegQuality = jpegQuality;
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

    public synchronized void setFromServerSettings(ServerSettings serverSettings) {
        setHeadlightOn(serverSettings.getHeadlightOn());
        setServoRotationAmount(serverSettings.getServoRotationAmount());
        setJpegQuality(serverSettings.getJpegQuality());
    }

    @NonNull
    public String toString() {
        StringBuffer sb = new StringBuffer();
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
