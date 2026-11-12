package net.dvmn2.logicplugin.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.dvmn2.logicplugin.storage.dto.CircuitDto;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Reads and writes {@code circuits/<uuid>.json}. Loading is blocking and meant for startup; writes go
 * through one dedicated thread so that writes of the same file stay ordered (a Bukkit async task pool
 * gives no such order). Thread-safe.
 */
public final class CircuitStore {

    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    private static final int SHUTDOWN_WAIT_SECONDS = 30;
    private static final String EXTENSION = ".json";

    private final Path directory;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().create();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "LogicPlugin-Storage");
        thread.setDaemon(false);
        return thread;
    });

    public CircuitStore(Path dataFolder, Logger logger) {
        this.directory = dataFolder.resolve("circuits");
        this.logger = logger;
    }

    /** Loads every circuit file; broken files are reported and skipped. Blocking. */
    public List<CircuitDto> loadAll() {
        List<CircuitDto> result = new ArrayList<>();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Cannot create " + directory, exception);
            return result;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(EXTENSION))::iterator) {
                CircuitDto dto = read(file);
                if (dto != null) {
                    result.add(dto);
                }
            }
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Cannot list " + directory, exception);
        }
        return result;
    }

    /** Queues a write of a snapshot. */
    public void saveAsync(CircuitDto dto) {
        writer.execute(() -> write(dto));
    }

    /** Queues removal of a circuit file. */
    public void deleteAsync(UUID id) {
        writer.execute(() -> {
            try {
                Files.deleteIfExists(file(id.toString()));
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Cannot delete circuit file " + id, exception);
            }
        });
    }

    /** Blocks until every queued write finished, then stops the writer. */
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning("Storage did not finish writing in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private CircuitDto read(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                logger.warning("Skipping oversized file " + file.getFileName());
                return null;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                CircuitDto dto = gson.fromJson(reader, CircuitDto.class);
                if (dto == null) {
                    logger.warning("Skipping empty file " + file.getFileName());
                }
                return dto;
            }
        } catch (IOException | JsonParseException exception) {
            logger.log(Level.WARNING, "Cannot read " + file.getFileName(), exception);
            return null;
        }
    }

    private void write(CircuitDto dto) {
        Path target = file(dto.id);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(dto, out);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Cannot save circuit " + dto.name, exception);
        }
    }

    private Path file(String id) {
        return directory.resolve(id + EXTENSION);
    }
}
