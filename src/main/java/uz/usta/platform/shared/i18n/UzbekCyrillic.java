package uz.usta.platform.shared.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O'zbek lotin yozuvidagi matnni kirillga o'giradi — katalogga kirillcha nom kiritilmagan bo'lsa ishlatiladi.
 * Qisqartmalar (SMS, UZS), camelCase nomlar (pushToken), {@code {0}} o'rinbosarlari va o'zbek alifbosida yo'q
 * harfli so'zlar o'zgarmaydi. Ruscha o'zlashma so'zlardagi yumshatish belgisi tiklanmaydi — aniq yozuv kerak
 * bo'lsa {@code uzCyrl} qiymati qo'lda kiritiladi.
 */
public final class UzbekCyrillic {

    /** O'rinbosar, yoki harf/raqam/tutuq belgisidan iborat so'z. */
    private static final Pattern TOKEN = Pattern.compile("\\{[^}]*}|\\p{L}[\\p{L}\\p{N}'ʻʼ‘’`]*");
    private static final String APOSTROPHES = "'ʻʼ‘’`";
    private static final String VOWELS = "aeiou";
    private static final Map<Character, String> LETTERS = Map.ofEntries(
            Map.entry('a', "а"), Map.entry('b', "б"), Map.entry('d', "д"), Map.entry('f', "ф"),
            Map.entry('g', "г"), Map.entry('h', "ҳ"), Map.entry('i', "и"), Map.entry('j', "ж"),
            Map.entry('k', "к"), Map.entry('l', "л"), Map.entry('m', "м"), Map.entry('n', "н"),
            Map.entry('o', "о"), Map.entry('p', "п"), Map.entry('q', "қ"), Map.entry('r', "р"),
            Map.entry('s', "с"), Map.entry('t', "т"), Map.entry('u', "у"), Map.entry('v', "в"),
            Map.entry('x', "х"), Map.entry('y', "й"), Map.entry('z', "з"));

    private UzbekCyrillic() {
    }

    public static String transliterate(String latin) {
        if (latin == null) {
            return null;
        }
        Matcher m = TOKEN.matcher(latin);
        StringBuilder out = new StringBuilder(latin.length());
        while (m.find()) {
            String token = m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(keepAsIs(token) ? token : word(token)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean keepAsIs(String token) {
        if (token.charAt(0) == '{') {
            return true;
        }
        int upper = 0;
        boolean lower = false;
        boolean upperInside = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isUpperCase(c)) {
                upper++;
                upperInside |= i > 0 && Character.isLowerCase(token.charAt(i - 1));
            }
            lower |= Character.isLowerCase(c);
            if (Character.isDigit(c) || c == 'w' || c == 'W') {
                return true;
            }
            if ((c == 'c' || c == 'C') && (i + 1 >= token.length() || Character.toLowerCase(token.charAt(i + 1)) != 'h')) {
                return true;
            }
        }
        return upper >= 2 && !lower || upperInside;
    }

    private static String word(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < lower.length()) {
            char c = lower.charAt(i);
            char next = i + 1 < lower.length() ? lower.charAt(i + 1) : 0;
            char third = i + 2 < lower.length() ? lower.charAt(i + 2) : 0;
            String cyr = null;
            int used = 1;
            if ((c == 'o' || c == 'g') && isApostrophe(next)) {
                cyr = c == 'o' ? "ў" : "ғ";
                used = 2;
            } else if (c == 'y' && next == 'o' && !isApostrophe(third)) {
                cyr = "ё";
                used = 2;
            } else if (c == 'y' && (next == 'u' || next == 'a' || next == 'e')) {
                cyr = next == 'u' ? "ю" : next == 'a' ? "я" : "е";
                used = 2;
            } else if (c == 's' && next == 'h') {
                cyr = "ш";
                used = 2;
            } else if (c == 'c' && next == 'h') {
                cyr = "ч";
                used = 2;
            } else if (c == 't' && next == 's' && (i == 0 || third == 'i')) {
                // "tsex", "-tsiya" — ц; "ketsa", "o'tsa" kabi qo'shimchalarda т+с qoladi
                cyr = "ц";
                used = 2;
            } else if (c == 'e') {
                boolean softE = i == 0 || VOWELS.indexOf(lower.charAt(i - 1)) >= 0;
                cyr = softE ? "э" : "е";
            } else if (isApostrophe(c) && i > 0 && lower.charAt(i - 1) == 's' && next == 'h') {
                // "Is'hoq" -> Исҳоқ: belgi faqat s va h ni ajratadi
                cyr = matchCase(source.charAt(i + 1), "ҳ");
                used = 2;
            } else if (isApostrophe(c)) {
                cyr = next == 0 ? null : "ъ"; // so'z oxiridagi belgi — qo'shtirnoq, o'zgarmaydi
            } else {
                cyr = LETTERS.get(c);
            }
            out.append(cyr == null ? String.valueOf(source.charAt(i)) : matchCase(source.charAt(i), cyr));
            i += used;
        }
        return out.toString();
    }

    private static boolean isApostrophe(char c) {
        return c != 0 && APOSTROPHES.indexOf(c) >= 0;
    }

    private static String matchCase(char source, String cyr) {
        return Character.isUpperCase(source) ? cyr.toUpperCase(Locale.ROOT) : cyr;
    }
}
