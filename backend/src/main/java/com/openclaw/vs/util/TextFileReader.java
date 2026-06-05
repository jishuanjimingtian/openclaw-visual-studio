package com.openclaw.vs.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Markdown memory files on Windows where both UTF-8 and GBK occur.
 * Tries strict UTF-8 and GBK, then picks the decode with better CJK / fewer mojibake signals.
 */
public final class TextFileReader {

    private static final Logger log = LoggerFactory.getLogger(TextFileReader.class);
    private static final Charset GBK = Charset.forName("GBK");

    private TextFileReader() {
    }

    public static String readString(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length == 0) {
            return "";
        }

        if (bytes.length >= 3
            && bytes[0] == (byte) 0xEF
            && bytes[1] == (byte) 0xBB
            && bytes[2] == (byte) 0xBF) {
            return decodeStrict(copyOfRange(bytes, 3, bytes.length), StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return decodeStrict(copyOfRange(bytes, 2, bytes.length), StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return decodeStrict(copyOfRange(bytes, 2, bytes.length), StandardCharsets.UTF_16BE);
        }

        List<Candidate> candidates = new ArrayList<>(2);
        addCandidate(candidates, tryDecodeStrict(bytes, StandardCharsets.UTF_8));
        addCandidate(candidates, tryDecodeStrict(bytes, GBK));

        if (candidates.isEmpty()) {
            log.warn("Reading {} with ISO-8859-1 fallback", path);
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }

        Candidate best = candidates.stream()
            .max((a, b) -> Integer.compare(scoreText(a.text()), scoreText(b.text())))
            .orElseThrow();

        if (candidates.size() > 1 && best.charset() != StandardCharsets.UTF_8) {
            log.debug("Read {} as {} (better than UTF-8 for CJK content)", path, best.charset().name());
        }

        return best.text();
    }

    private record Candidate(Charset charset, String text) {
    }

    private static void addCandidate(List<Candidate> list, Candidate c) {
        if (c != null) {
            list.add(c);
        }
    }

    private static Candidate tryDecodeStrict(byte[] bytes, Charset charset) {
        try {
            return new Candidate(charset, decodeStrict(bytes, charset));
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static byte[] copyOfRange(byte[] bytes, int from, int to) {
        int len = to - from;
        byte[] out = new byte[len];
        System.arraycopy(bytes, from, out, 0, len);
        return out;
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes));
        return chars.toString();
    }

    /**
     * Higher score = more likely correct Chinese / Markdown text.
     */
    static int scoreText(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int han = 0;
        int penalty = 0;
        for (int cp : s.codePoints().toArray()) {
            if (cp == 0xFFFD) {
                penalty += 20;
                continue;
            }
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                han++;
                continue;
            }
            if (cp >= 0x80 && cp <= 0x024F) {
                penalty += 1;
            }
        }
        if (s.contains("锟斤拷")) {
            penalty += 80;
        }
        for (String marker : List.of("Ã", "Â", "ï¿", "ã€", "æ—", "å¥")) {
            if (s.contains(marker)) {
                penalty += 8;
            }
        }
        int score = han * 12 - penalty;
        if (han > 0 && han * 30 >= s.codePointCount(0, s.length())) {
            score += 15;
        }
        int latinExtended = 0;
        for (int cp : s.codePoints().toArray()) {
            if (cp >= 0x00C0 && cp <= 0x024F) {
                latinExtended++;
            }
        }
        if (latinExtended > han && latinExtended > 3) {
            penalty += 40;
        }
        return score;
    }

    /** Write UTF-8 with BOM so Windows tools consistently detect encoding. */
    public static byte[] withUtf8Bom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF;
        out[1] = (byte) 0xBB;
        out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }
}
