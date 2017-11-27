package com.backyardbrains.data;

import android.support.annotation.Nullable;
import com.backyardbrains.audio.RingBuffer;
import com.backyardbrains.utils.AudioUtils;
import com.backyardbrains.utils.LogUtils;
import java.nio.ByteBuffer;

/**
 * @author Tihomir Leka <ticapeca at gmail.com>
 */
public class DataManager {

    private static final String TAG = LogUtils.makeLogTag(DataManager.class);

    //
    private static final int DEFAULT_BUFFER_SIZE = AudioUtils.SAMPLE_RATE * 6; // 6 seconds

    private RingBuffer dataBuffer;
    private long lastBytePosition;

    private static DataManager manager;

    // Private constructor through which we create singleton instance
    private DataManager() {
        dataBuffer = new RingBuffer(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Returns singleton instance of {@link DataManager} with default configuration.
     */
    public static DataManager get() {
        if (manager == null) manager = new DataManager();

        return manager;
    }

    //=================================================
    //  PUBLIC METHODS
    //=================================================

    /**
     * Sets buffer size of the {@link RingBuffer}.
     */
    public void setBufferSize(int bufferSize) {
        if (bufferSize <= 0) throw new IllegalArgumentException("Buffer can't be 0 or negative value!");

        dataBuffer.clear();
        // if buffer size is negative or zero use default buffer size
        dataBuffer = new RingBuffer(bufferSize);
    }

    /**
     * Sets buffer size of the {@link RingBuffer} to default value (6 sec of audio at 44.1 kHz)
     */
    public void resetBufferSize() {
        dataBuffer.clear();
        // if buffer size is negative or zero use default buffer size
        dataBuffer = new RingBuffer(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Returns an array of shorts that are representing the sample data.
     *
     * @return a ordinate-corrected version of the audio buffer
     */
    public short[] getData() {
        return dataBuffer != null ? dataBuffer.getArray() : new short[0];
    }

    /**
     * Adds specified {@code data} to ring buffer and saves position of the last added byte
     */
    public void addToBuffer(@Nullable ByteBuffer data, long lastBytePosition) {
        // just return if data is null
        if (data == null) return;

        // add data to ring buffer
        if (dataBuffer != null) dataBuffer.add(data);

        // last played byte position
        this.lastBytePosition = lastBytePosition;
    }

    /**
     * Adds specified {@code data} to ring buffer
     */
    public void addToBuffer(@Nullable short[] data) {
        // just return if data is null
        if (data == null) return;

        // add data to ring buffer
        if (dataBuffer != null) dataBuffer.add(data);
    }

    public void addToBuffer(short sample) {
        if (dataBuffer != null) dataBuffer.add(sample);
    }

    /**
     * Clears the ring buffer and resets last read byte position
     */
    public void clearBuffer() {
        if (dataBuffer != null) {
            dataBuffer.clear();
            lastBytePosition = 0;
        }
    }

    /**
     * Returns last read byte position.
     */
    public long getLastBytePosition() {
        return lastBytePosition;
    }
}
