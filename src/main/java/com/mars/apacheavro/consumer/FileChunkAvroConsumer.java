package com.mars.apacheavro.consumer;

import com.mars.apacheavro.dto.FileChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.*;
import java.util.Comparator;

@Service
public class FileChunkAvroConsumer {

    @Value("${app.output-dir}") private String outputDir;

    @KafkaListener(topics = "${topic.file}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(FileChunk chunk) throws Exception {
        String fileId = chunk.getFileId().toString();
        String fileName = chunk.getFileName().toString();
        int idx = chunk.getChunkIndex();
        int total = chunk.getTotalChunks();

        Path outBase = Paths.get(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(outBase);

        // temp: /data/out/.tmp/<fileId>/
        Path tmpDir = outBase.resolve(".tmp").resolve(fileId).normalize();
        Files.createDirectories(tmpDir);

        Path partPath = tmpDir.resolve(String.format("%06d.part", idx));
        byte[] data = toBytes(chunk.getData());
        Files.write(partPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        if (isComplete(tmpDir, total)) {
            Path finalPath = outBase.resolve(fileId + "__" + fileName).normalize();
            merge(tmpDir, finalPath, total);
            deleteDirectory(tmpDir);

            System.out.println("Merged OK -> " + finalPath + " (" + Files.size(finalPath) + " bytes)");
        }
    }

    private static byte[] toBytes(java.nio.ByteBuffer buffer) {
        java.nio.ByteBuffer dup = buffer.duplicate();
        byte[] b = new byte[dup.remaining()];
        dup.get(b);
        return b;
    }

    private static boolean isComplete(Path tmpDir, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            if (!Files.exists(tmpDir.resolve(String.format("%06d.part", i)))) return false;
        }
        return true;
    }

    private static void merge(Path tmpDir, Path finalPath, int totalChunks) throws Exception {
        try (OutputStream out = Files.newOutputStream(finalPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < totalChunks; i++) {
                Files.copy(tmpDir.resolve(String.format("%06d.part", i)), out);
            }
        }
    }

    private static void deleteDirectory(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
    }
}
