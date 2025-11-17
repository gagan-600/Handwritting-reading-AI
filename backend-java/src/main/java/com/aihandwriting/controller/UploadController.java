package com.aihandwriting.controller;

import com.aihandwriting.service.AIService;
import com.aihandwriting.service.OCRService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UploadController {

    private final OCRService ocrService;
    private final AIService aiService;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/upload")
    public ResponseEntity<?> handleUpload(@RequestParam("file") MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload-", file.getOriginalFilename());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }

            String extractedText = ocrService.extractText(tempFile);
            System.out.println("🧠 Extracted Text: " + extractedText); // print in console

            // Get structured JSON from AIService (string) and parse to JSON node
            String structuredJson = aiService.extractStructuredData(extractedText);
            JsonNode structuredNode = mapper.readTree(structuredJson);

            // Return the structured node directly as the response body
            return ResponseEntity.ok(structuredNode);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
