package com.aster.app.rag.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * PDF 文本解析器。
 */
public class PdfDocumentParser implements DocumentParser {
    /**
     * 使用 PDFBox 提取 PDF 文本。
     *
     * <p>setSortByPosition(true) 可以让部分 PDF 的文字顺序更接近视觉阅读顺序。</p>
     */
    @Override
    public String parse(Path sourceFile) throws IOException {
        try (PDDocument document = PDDocument.load(sourceFile.toFile())) {
            AccessPermission permission = document.getCurrentAccessPermission();
            if (!permission.canExtractContent()) {
                throw new IOException("PDF 不允许提取文本: " + sourceFile.getFileName());
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    @Override
    public boolean supports(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || type.contains("pdf");
    }
}
