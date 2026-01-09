package com.mars.apacheavro.controller;

import com.mars.apacheavro.producer.FileChunkAvroProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileChunkAvroProducer producer;

    public FileController(FileChunkAvroProducer producer) {
        this.producer = producer;
    }

    public record SendFileRequest(String fileName) {}

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody SendFileRequest req) throws Exception {
        String fileId = producer.sendFromInputDir(req.fileName());
        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "fileId", fileId,
                "fileName", req.fileName()
        ));
    }
}
