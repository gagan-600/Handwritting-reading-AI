package com.aihandwriting.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class OCRService {

    private final ITesseract tesseract;

  public OCRService() {
    this.tesseract = new Tesseract();

    // ✅ Path where Tesseract is installed
    this.tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");

    // ✅ Language file
    this.tesseract.setLanguage("eng");
}


    public String extractText(File imageFile) {
        try {
            return tesseract.doOCR(imageFile);
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed: " + e.getMessage(), e);
        }
    }
}
//tesseract "C:\Users\Aseuro\Pictures\Screenshot2.jpg" stdout 