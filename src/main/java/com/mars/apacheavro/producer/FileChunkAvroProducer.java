package com.mars.apacheavro.producer;

import com.mars.apacheavro.dto.FileChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;

@Service
public class FileChunkAvroProducer {

    @Value("${topic.file}") private String topic;
    @Value("${app.input-dir}") private String inputDir;
    @Value("${app.chunk-size-bytes}") private int chunkSize;

    private final KafkaTemplate<String, FileChunk> template;

    public FileChunkAvroProducer(KafkaTemplate<String, FileChunk> template) {
        this.template = template;
    }

    public String sendFromInputDir(String fileName) throws Exception {
        validateFileName(fileName);

        Path base = Paths.get(inputDir).toAbsolutePath().normalize();
        Path path = base.resolve(fileName).normalize();

        if (!path.startsWith(base)) throw new IllegalArgumentException("Path traversal detected");
        if (!Files.exists(path) || !Files.isRegularFile(path)) throw new IllegalArgumentException("File not found: " + fileName);

        String fileId = UUID.randomUUID().toString();
        long size = Files.size(path);
        int totalChunks = (int) Math.ceil((double) size / chunkSize);

        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            byte[] buf = new byte[chunkSize];
            int idx = 0;
            int read;

            while ((read = in.read(buf)) != -1) {
                byte[] data = (read == buf.length) ? buf : Arrays.copyOf(buf, read);

                FileChunk chunk = FileChunk.newBuilder()
                        .setFileId(fileId)
                        .setFileName(fileName)
                        .setChunkIndex(idx)
                        .setTotalChunks(totalChunks)
                        .setData(ByteBuffer.wrap(data))
                        .build();

                // key=fileId => cùng partition => giữ thứ tự chunks
                template.send(topic, fileId, chunk);
                idx++;
            }
        }

        return fileId;
    }

    private static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName is required");
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid fileName (only file name, no path)");
        }
    }
}