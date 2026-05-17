package com.basir.ai;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Single in-process holder for the current (or last) conversion job, so the
 * UI can survive {@link ConversionService} starting/stopping and the user
 * backgrounding/foregrounding the app at any moment.
 *
 * The state object is intentionally tiny and synchronous: every mutation is
 * immediately broadcast to all registered listeners on the main thread.
 */
public final class ConversionState {

    public enum Status { IDLE, RUNNING, SUCCESS, FAILED }

    public interface Listener {
        void onConversionStateChanged(ConversionState state);
    }

    private static final ConversionState INSTANCE = new ConversionState();
    public static ConversionState get() { return INSTANCE; }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private Status status = Status.IDLE;
    private int currentPage = 0;
    private int totalPages = 0;
    private File resultFile;
    private String errorMessage;

    private ConversionState() {}

    public synchronized Status status() { return status; }
    public synchronized int current() { return currentPage; }
    public synchronized int total()   { return totalPages; }
    public synchronized File result() { return resultFile; }
    public synchronized String error() { return errorMessage; }
    public synchronized boolean isRunning() { return status == Status.RUNNING; }

    public void addListener(Listener l) {
        synchronized (this) {
            if (!listeners.contains(l)) listeners.add(l);
        }
        // Immediately deliver current snapshot.
        notifyOne(l);
    }

    public void removeListener(Listener l) {
        synchronized (this) { listeners.remove(l); }
    }

    void start() {
        synchronized (this) {
            status = Status.RUNNING;
            currentPage = 0;
            totalPages = 0;
            resultFile = null;
            errorMessage = null;
        }
        broadcast();
    }

    void updateProgress(int current, int total) {
        synchronized (this) {
            this.currentPage = current;
            this.totalPages = total;
        }
        broadcast();
    }

    void success(File file) {
        synchronized (this) {
            status = Status.SUCCESS;
            resultFile = file;
            if (totalPages > 0) currentPage = totalPages;
        }
        broadcast();
    }

    void fail(String message) {
        synchronized (this) {
            status = Status.FAILED;
            errorMessage = message;
        }
        broadcast();
    }

    /** Consumed by the UI after it handles a terminal state. */
    public void clear() {
        synchronized (this) {
            status = Status.IDLE;
            currentPage = 0;
            totalPages = 0;
            resultFile = null;
            errorMessage = null;
        }
        broadcast();
    }

    private void broadcast() {
        final List<Listener> snapshot;
        synchronized (this) { snapshot = new ArrayList<>(listeners); }
        ui.post(() -> {
            for (Listener l : snapshot) {
                try { l.onConversionStateChanged(this); } catch (Throwable ignore) {}
            }
        });
    }

    private void notifyOne(Listener l) {
        ui.post(() -> {
            try { l.onConversionStateChanged(this); } catch (Throwable ignore) {}
        });
    }
}
