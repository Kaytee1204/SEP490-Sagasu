package com.example.base.service.claim;

import com.example.base.dto.claim.ClaimComparisonDto;
import com.example.base.dto.claim.ClaimReviewSummaryDto.Source;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Closed vocabulary boundary: no substring of a user's answer is ever returned to the provider. */
@Component
public class ClaimSummarySanitizer {
    public static final List<String> CRITERIA = List.of("identity", "appearance", "accessories");
    public static final List<String> LABELS = List.of("Loại đồ / Hãng", "Màu sắc / Chất liệu", "Phụ kiện đi kèm");
    public record Fact(String id, String text, Source source) {}
    public record SafeRow(String alias, Map<String, List<Fact>> criteria) {}
    private record Term(String text, List<String> aliases) {}
    private static Term term(String text, String... aliases) { return new Term(text, List.of(aliases)); }
    private static final List<Term> BRANDS = List.of(
        term("Apple", "apple", "iphone", "ipad", "macbook"), term("Samsung", "samsung"),
        term("Xiaomi", "xiaomi", "redmi", "ximi"), term("OPPO", "oppo"), term("Vivo", "vivo"),
        term("Realme", "realme"), term("Nokia", "nokia"), term("Huawei", "huawei"),
        term("Dell", "dell"), term("HP", "hp"), term("Asus", "asus"), term("Acer", "acer"),
        term("Lenovo", "lenovo"), term("Sony", "sony"), term("JBL", "jbl"), term("Logitech", "logitech"),
        term("Lock&Lock", "lock&lock", "lock lock"), term("Thermos", "thermos"), term("Stanley", "stanley"),
        term("Nike", "nike"), term("Adidas", "adidas"), term("Uniqlo", "uniqlo"), term("Converse", "converse"),
        term("Casio", "casio"), term("Seiko", "seiko"), term("Ray-Ban", "ray ban"), term("Pedro", "pedro"));
    private static final List<Term> TYPES = List.of(
        term("Điện thoại", "dien thoai", "iphone", "redmi"), term("Laptop", "laptop", "macbook"),
        term("Máy tính bảng", "may tinh bang", "ipad"), term("Tai nghe", "tai nghe"),
        term("Chuột", "chuot"), term("Bàn phím", "ban phim"), term("Balo", "balo", "ba lo"),
        term("Túi xách", "tui xach"), term("Túi đeo chéo", "tui deo cheo"), term("Ví", "vi"),
        term("Bình nước", "binh nuoc", "binh giu nhiet"), term("Hộp cơm", "hop com"), term("Ô", "o", "du"),
        term("Thú bông", "thu bong"), term("Đồ chơi", "do choi"), term("Quần áo", "quan ao", "ao", "quan", "ao khoac"),
        term("Mũ", "mu", "non"), term("Giày", "giay the thao", "giay da", "giay"), term("Dép", "dep"),
        term("Kính", "kinh"), term("Đồng hồ", "dong ho"), term("Trang sức", "trang suc"),
        term("Sách", "sach"), term("Vở", "vo"), term("Bút", "but"), term("Hộp bút", "hop but"));
    private static final List<Term> COLORS = List.of(
        term("Màu đen", "den", "black"), term("Màu trắng", "trang", "white"),
        term("Màu xanh dương", "xanh duong", "xanh bien", "blue"), term("Màu xanh lá", "xanh la", "green"),
        term("Màu xanh (chưa rõ sắc độ)", "xanh"), term("Màu đỏ", "do", "red"), term("Màu vàng", "vang", "yellow"),
        term("Màu hồng", "hong", "pink"), term("Màu tím", "tim", "purple"), term("Màu nâu", "nau", "brown"),
        term("Màu xám", "xam", "grey", "gray"), term("Màu bạc", "bac", "silver"), term("Màu cam", "cam", "orange"));
    private static final List<Term> MATERIALS = List.of(
        term("Chất liệu da", "da"), term("Chất liệu vải", "vai"), term("Chất liệu nhựa", "nhua"),
        term("Chất liệu kim loại", "kim loai"), term("Chất liệu inox", "inox"), term("Chất liệu thủy tinh", "thuy tinh"),
        term("Chất liệu silicone", "silicone", "silicon"), term("Chất liệu cao su", "cao su"));
    private static final List<Term> ACCESSORIES = List.of(
        term("Ốp bảo vệ", "op", "op lung"), term("Bao / hộp đựng", "bao dung", "bao bao ve", "hop dung"),
        term("Bộ sạc", "sac", "cu sac"), term("Dây cáp", "day cap", "cap sac"), term("Chuột đi kèm", "chuot"),
        term("USB đi kèm", "usb"), term("Dây đeo", "day deo"), term("Quai cầm", "quai", "tay cam"),
        term("Ống hút", "ong hut"), term("Nắp đậy", "nap"), term("Túi đựng", "tui dung", "balo"));
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?:@|https?://|www\\.|[0-9]{5,}|\\b(?:cccd|cmnd|cvv|cvc|otp|pin|imei|serial|password)\\b|"
        + "\\b(?:ho ten|ten toi|ten la|ghi ten|khac ten|so the|so dien thoai|dien thoai lien he|dia chi|mat khau|ma so|ma bao mat|bi mat|rieng tu|bao mat)\\b)");

    public List<SafeRow> sanitize(List<ClaimComparisonDto.Entry> entries) {
        var rows = new ArrayList<SafeRow>();
        int nextFact = 0;
        for (int index = 0; index < entries.size(); index++) {
            var criteria = new LinkedHashMap<String, List<Fact>>();
            CRITERIA.forEach(c -> criteria.put(c, new ArrayList<>()));
            for (var answer : entries.get(index).answers() == null ? List.<ClaimComparisonDto.Answer>of() : entries.get(index).answers()) {
                // Unknown versions and newly added questions require a deliberate policy review.
                if (answer.templateVersion() != 1 || answer.answerText() == null || answer.answerText().length() > 4000) continue;
                String policy = policy(answer.templateCode(), answer.questionCode());
                String text = normalize(answer.answerText());
                if (policy.isEmpty() || SENSITIVE.matcher(text).find()) continue;
                var source = new Source(answer.templateCode(), answer.templateVersion(), answer.questionCode());
                var extracted = new LinkedHashMap<String, List<String>>();
                for (String criterion : CRITERIA) extracted.put(criterion, new ArrayList<>());
                if (policy.contains("I")) {
                    if (answer.questionCode().equals("Q1")) extract(text, TYPES, extracted.get("identity"));
                    extract(text, BRANDS, extracted.get("identity"));
                }
                if (policy.contains("A")) {
                    extractDescriptive(answer.answerText(), text, COLORS, "Màu ", extracted.get("appearance"));
                    extractDescriptive(answer.answerText(), text, MATERIALS, "Chất liệu ", extracted.get("appearance"));
                    if (extracted.get("appearance").stream().anyMatch(v -> v.startsWith("Màu xanh ") && !v.contains("chưa rõ")))
                        extracted.get("appearance").remove("Màu xanh (chưa rõ sắc độ)");
                }
                if (policy.contains("X")) {
                    String noAccessories = explicitNo(answer.templateCode(), answer.questionCode());
                    if ((text.equals("khong") || text.startsWith("khong\n")) && noAccessories != null)
                        extracted.get("accessories").add(noAccessories);
                    else if (!text.startsWith("khong ro") && !text.startsWith("khong ap dung")) {
                        extract(text, ACCESSORIES, extracted.get("accessories"));
                        // Keep accessory color in the accessory criterion, not the body-color criterion.
                        extractDescriptive(answer.answerText(), text, COLORS, "Màu ", extracted.get("accessories"));
                        if (text.equals("co") && noAccessories != null && extracted.get("accessories").isEmpty())
                            extracted.get("accessories").add("Có phụ kiện, chưa mô tả loại");
                    }
                }
                for (String criterion : CRITERIA) for (String point : extracted.get(criterion)) {
                    var facts = criteria.get(criterion);
                    if (facts.size() < 8 && facts.stream().noneMatch(f -> f.text().equals(point)))
                        facts.add(new Fact("F" + nextFact++, point, source));
                }
            }
            var frozen = new LinkedHashMap<String, List<Fact>>();
            criteria.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
            rows.add(new SafeRow("R" + (index + 1), Collections.unmodifiableMap(frozen)));
        }
        return List.copyOf(rows);
    }

    // I: generic identity/brand, A: color/material, X: generic accessories. C1-C4 and ID_CARD excluded.
    private String policy(String template, String question) {
        if (template == null || question == null) return "";
        return switch (template + ":" + question) {
            case "PHONE:Q1", "LAPTOP:Q1", "BAG:Q1", "BAG:Q2",
                 "BOTTLE:Q1", "CLOTHING:Q1", "CLOTHING:Q2", "FOOTWEAR:Q1", "FOOTWEAR:Q2",
                 "ACCESSORY:Q1", "ACCESSORY:Q2", "EYEWEAR:Q2", "STATIONERY:Q1", "STATIONERY:Q2",
                 "CHILDREN:Q1", "CHILDREN:Q2", "OTHER:Q3" -> "I";
            case "PHONE:Q2", "LAPTOP:Q2", "BAG:Q3", "BOTTLE:Q2", "BOTTLE:Q3", "PERSONAL:Q3",
                 "CLOTHING:Q3", "CLOTHING:Q5", "FOOTWEAR:Q3", "FOOTWEAR:Q5", "ACCESSORY:Q3",
                 "EYEWEAR:Q3", "EYEWEAR:Q4", "STATIONERY:Q3", "CHILDREN:Q3", "OTHER:Q2" -> "A";
            case "ELECTRONICS:Q1", "ELECTRONICS:Q2", "PERSONAL:Q1", "PERSONAL:Q2", "WALLET:Q2", "OTHER:Q1" -> "IA";
            case "PHONE:Q3", "LAPTOP:Q5", "ELECTRONICS:Q5", "ELECTRONICS:Q6", "PERSONAL:Q4",
                 "PERSONAL:Q5", "BOTTLE:Q4", "BOTTLE:Q6", "ACCESSORY:Q5", "EYEWEAR:Q5",
                 "CHILDREN:Q5", "OTHER:Q4" -> "X";
            default -> "";
        };
    }

    private String explicitNo(String template, String question) {
        return switch (template + ":" + question) {
            case "PHONE:Q3" -> "Không dùng ốp / phụ kiện gắn máy";
            case "ELECTRONICS:Q5" -> "Không có vỏ / bao / ốp bảo vệ";
            case "LAPTOP:Q5", "PERSONAL:Q5", "BOTTLE:Q6", "ACCESSORY:Q5", "EYEWEAR:Q5", "CHILDREN:Q5", "OTHER:Q4"
                    -> "Không có phụ kiện (theo câu trả lời)";
            default -> null;
        };
    }

    private static void extract(String text, List<Term> dictionary, List<String> result) {
        for (Term term : dictionary) for (String alias : term.aliases()) {
            var matcher = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])").matcher(text);
            boolean found = false;
            while (matcher.find()) {
                String before = text.substring(Math.max(0, matcher.start() - 22), matcher.start());
                if (!before.matches("(?s).*\\b(khong|khong co|khong phai|khong dung|chua co)(?:\\s+mau)?\\s+$")) { found = true; break; }
            }
            if (found) { result.add(term.text()); break; }
        }
    }
    private static void extractDescriptive(String original, String normalized, List<Term> dictionary, String prefix, List<String> result) {
        String lower = original.toLowerCase(Locale.ROOT);
        for (Term term : dictionary) {
            String physical = term.text().substring(prefix.length()).replace(" (chưa rõ sắc độ)", "").toLowerCase(Locale.ROOT);
            boolean exact = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(physical) + "(?![\\p{L}\\p{N}])").matcher(lower).find();
            // Preserve accents to avoid treating 'đồ' as 'đỏ', 'đã' as leather, or 'tìm' as purple.
            boolean qualified = term.aliases().stream().anyMatch(alias -> normalized.contains(normalize(prefix) + " " + alias));
            boolean english = term.aliases().stream().anyMatch(alias -> List.of("black", "white", "blue", "green", "red", "yellow", "pink", "purple", "brown", "grey", "gray", "silver", "orange").contains(alias)
                    && Pattern.compile("\\b" + alias + "\\b").matcher(normalized).find());
            if (exact || qualified || english) extract(normalized, List.of(term), result);
        }
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").strip();
    }
}
