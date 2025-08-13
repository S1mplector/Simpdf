package com.pdfreader.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple JSON-backed scan state store. Persists per-file metadata (size, mtime)
 * so rescans can skip unchanged files.
 */
public class ScanStateStore {
    private final ObjectMapper objectMapper;
    private final Path stateFile;

    private Map<String, FileState> state = Collections.synchronizedMap(new HashMap<>());

    public ScanStateStore(String dataDir, String fileName) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        Path dir = Paths.get(dataDir);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create data directory: " + dataDir, e);
            }
        }
        this.stateFile = dir.resolve(fileName);
        load();
    }

    public synchronized void load() {
        if (Files.exists(stateFile)) {
            try {
                byte[] bytes = Files.readAllBytes(stateFile);
                if (bytes.length > 0) {
                    Map<String, FileState> map = objectMapper.readValue(bytes, new TypeReference<Map<String, FileState>>() {});
                    state.clear();
                    state.putAll(map);
                }
            } catch (IOException e) {
                // ignore, start empty
            }
        }
    }

    public synchronized void persist() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist scan state", e);
        }
    }

    public FileState get(String path) {
        return state.get(path);
    }

    public void put(String path, FileState fileState) {
        state.put(path, fileState);
    }

    public void remove(String path) {
        state.remove(path);
    }

    public Map<String, FileState> snapshot() {
        return new HashMap<>(state);
    }

    public static class FileState {
        private long size;
        private long mtime;

        public FileState() {}
        public FileState(long size, long mtime) {
            this.size = size;
            this.mtime = mtime;
        }

        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public long getMtime() { return mtime; }
        public void setMtime(long mtime) { this.mtime = mtime; }
    }
}
