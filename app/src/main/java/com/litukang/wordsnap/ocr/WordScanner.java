package com.litukang.wordsnap.ocr;

import android.graphics.Bitmap;
import android.os.SystemClock;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.litukang.wordsnap.data.WordRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 端侧 OCR：把一帧截图里的英文单词挑出来，到本地词库里查释义。
 * 全程离线（ML Kit 拉丁脚本模型已打包进 APK）。
 */
public class WordScanner {

    public static class Hit implements java.io.Serializable {
        public final String word;
        public final String meaning;

        public Hit(String word, String meaning) {
            this.word = word;
            this.meaning = meaning;
        }
    }

    public interface Callback {
        void onResult(List<Hit> hits, long costMs, String preview);

        void onFailure(Exception e);
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z']{1,}");
    private static final int MAX_HITS = 8;

    private final TextRecognizer recognizer;

    public WordScanner() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void scan(Bitmap bmp, WordRepository repo, Callback cb) {
        final long t0 = SystemClock.elapsedRealtime();
        InputImage image = InputImage.fromBitmap(bmp, 0);
        recognizer.process(image)
                .addOnSuccessListener(result -> cb.onResult(extract(result, repo),
                        SystemClock.elapsedRealtime() - t0, preview(result)))
                .addOnFailureListener(cb::onFailure);
    }

    /** 只要 OCR 出的"文本行"，不查词 —— 答题引擎要靠这些行切分题干和选项 */
    public interface LinesCallback {
        void onLines(List<String> lines, long costMs);

        void onFailure(Exception e);
    }

    public void scanLines(Bitmap bmp, LinesCallback cb) {
        final long t0 = SystemClock.elapsedRealtime();
        InputImage image = InputImage.fromBitmap(bmp, 0);
        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    List<String> lines = new ArrayList<>();
                    for (Text.TextBlock block : result.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            String t = line.getText() == null ? "" : line.getText().trim();
                            if (!t.isEmpty()) {
                                lines.add(t);
                            }
                        }
                    }
                    cb.onLines(lines, SystemClock.elapsedRealtime() - t0);
                })
                .addOnFailureListener(cb::onFailure);
    }

    private static List<Hit> extract(Text result, WordRepository repo) {
        List<Hit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Matcher m = WORD.matcher(line.getText());
                while (m.find() && hits.size() < MAX_HITS) {
                    String token = m.group();
                    String meaning = repo.lookup(token);
                    if (meaning == null) {
                        continue;
                    }
                    String canonical = repo.canonicalOf(token);
                    String key = canonical != null ? canonical : token.toLowerCase();
                    if (seen.add(key)) {
                        hits.add(new Hit(key, meaning));
                    }
                }
                if (hits.size() >= MAX_HITS) {
                    break;
                }
            }
            if (hits.size() >= MAX_HITS) {
                break;
            }
        }
        return hits;
    }

    private static String preview(Text result) {
        String t = result.getText().replace('\n', ' ').trim();
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }

    public void close() {
        try {
            recognizer.close();
        } catch (Exception ignored) {
        }
    }
}
