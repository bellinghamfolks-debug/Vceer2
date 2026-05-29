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
 * Every mutation is broadcast on the main thread.
 */
public final class ConversionState {

    public enum Status { IDLE, RUNNING, SUCCESS, FAILED, CANCELLED }

    /** Coarse-grained progress phase, shown in the UI as a friendly label. */
    public enum Stage { PREPARING, UPLOADING, PROCESSING, FINALISING, DONE }

    public interface Listener {
        void onConversionStateChanged(ConversionState state);
    }

    private static final ConversionState INSTANCE = new ConversionState();
    public static ConversionState get() { return INSTANCE; }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private Status status = Status.IDLE;
    private Stage stage = Stage.PREPARING;
    private int currentPage = 0;
    private int totalPages = 0;
    private File resultFile;
    private String errorMessage;
    /** Set by the service when a cancel request arrives. The worker checks it
     *  on its next progress callback. */
    private volatile boolean cancelRequested = false;

    // v2.0 — Document Q&A. After a successful conversion we keep a reference
    // to the file that was uploaded to Gemini's Files API, so the user can
    // ask follow-up questions about it ("how much does this invoice total
    // come to?", "what's the address on page 3?") without re-uploading
    // anything. The file auto-expires on Gemini's side after 48 h.
    private String uploadedFileName;  // e.g. "files/abc123"
    private String uploadedFileUri;   // full URI for fileData.fileUri
    private String uploadedFileMime;  // e.g. "application/pdf"
    private String sourceDisplayName; // user-visible name of the original
    // v2.8.1 — the EXTRA_MODE value the caller passed in (e.g. "full",
    // "translate:fr"). Stored here so the success branch can derive a
    // meaningful output filename even after the Activity is recreated.
    private String requestedMode;

    private ConversionState() {}

    public synchronized Status status() { return status; }
    public synchronized Stage  stage()   { return stage; }
    public synchronized int current() { return currentPage; }
    public synchronized int total()   { return totalPages; }
    public synchronized File result() { return resultFile; }
    public synchronized String error() { return errorMessage; }
    public synchronized boolean isRunning() { return status == Status.RUNNING; }
    public boolean isCancelRequested() { return cancelRequested; }

    public synchronized String uploadedFileUri()  { return uploadedFileUri; }
    public synchronized String uploadedFileName() { return uploadedFileName; }
    public synchronized String uploadedFileMime() { return uploadedFileMime; }
    public synchronized String sourceDisplayName() { return sourceDisplayName; }
    public synchronized boolean hasUploadedFile() {
        return uploadedFileUri != null && !uploadedFileUri.isEmpty();
    }

    /** Called by the conversion pipeline as soon as the upload completes. */
    public void setUploadedFile(String name, String uri, String mimeType) {
        synchronized (this) {
            this.uploadedFileName = name;
            this.uploadedFileUri = uri;
            this.uploadedFileMime = mimeType;
        }
        broadcast();
    }

    /** Called by the picker when the user selects a source file. */
    public void setSourceDisplayName(String displayName) {
        synchronized (this) { this.sourceDisplayName = displayName; }
    }

    /** v2.8.1 — remembered across the lifetime of one conversion job. */
    public synchronized String requestedMode() { return requestedMode; }

    public void setRequestedMode(String mode) {
        synchronized (this) { this.requestedMode = mode; }
    }

    /** Wipe the uploaded-file reference. The file is left on Gemini's side
     *  to auto-expire — we only forget about it locally. */
    public void clearUploadedFile() {
        synchronized (this) {
            this.uploadedFileName = null;
            this.uploadedFileUri = null;
            this.uploadedFileMime = null;
            this.sourceDisplayName = null;
        }
        broadcast();
    }

    public void addListener(Listener l) {
        synchronized (this) {
            if (!listeners.contains(l)) listeners.add(l);
        }
        notifyOne(l);
    }

    public void removeListener(Listener l) {
        synchronized (this) { listeners.remove(l); }
    }

    void start() {
        synchronized (this) {
            status = Status.RUNNING;
            stage = Stage.PREPARING;
            currentPage = 0;
            totalPages = 0;
            resultFile = null;
            errorMessage = null;
            cancelRequested = false;
        }
        broadcast();
    }

    void updateProgress(int current, int total, Stage newStage) {
        synchronized (this) {
            this.currentPage = current;
            this.totalPages = total;
            if (newStage != null) this.stage = newStage;
        }
        broadcast();
    }

    void success(File file) {
        synchronized (this) {
            status = Status.SUCCESS;
            stage = Stage.DONE;
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

    void cancelled() {
        synchronized (this) {
            status = Status.CANCELLED;
            errorMessage = null;
            cancelRequested = false;
        }
        broadcast();
    }

    /** Asks the running job to stop at its next checkpoint. Picked up by the
     *  worker thread; the HTTP request may still complete its current batch. */
    public void requestCancel() {
        cancelRequested = true;
    }

    /** Consumed by the UI after it handles a terminal state. */
    public void clear() {
        synchronized (this) {
            status = Status.IDLE;
            stage = Stage.PREPARING;
            currentPage = 0;
            totalPages = 0;
            resultFile = null;
            errorMessage = null;
            cancelRequested = false;
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
