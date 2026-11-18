package com.hospital.crm.knowledge.application;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本解析与切片：按空行分段，超长段落按句子边界切分，单片不超过 maxChars。
 */
public final class TextChunker {

    public static final int MAX_CHUNK_CHARS = 500;

    private TextChunker() {
    }

    public static List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> pieces = new ArrayList<>();
        for (String paragraph : normalized.split("\n{2,}")) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                splitLong(trimmed, pieces);
            }
        }
        return merge(pieces);
    }

    private static void splitLong(String paragraph, List<String> out) {
        if (paragraph.length() <= MAX_CHUNK_CHARS) {
            out.add(paragraph);
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[。！？；!?;])")) {
            if (sentence.isBlank()) {
                continue;
            }
            if (current.length() + sentence.length() > MAX_CHUNK_CHARS) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                while (sentence.length() > MAX_CHUNK_CHARS) {
                    out.add(sentence.substring(0, MAX_CHUNK_CHARS));
                    sentence = sentence.substring(MAX_CHUNK_CHARS);
                }
                current.append(sentence);
            } else {
                current.append(sentence);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
    }

    private static List<String> merge(List<String> pieces) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() > 0 && current.length() + piece.length() + 1 > MAX_CHUNK_CHARS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(piece);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
