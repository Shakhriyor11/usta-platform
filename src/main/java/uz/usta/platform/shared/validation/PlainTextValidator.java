package uz.usta.platform.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PlainTextValidator implements ConstraintValidator<PlainText, CharSequence> {

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || isPlain(value);
    }

    public static boolean isPlain(CharSequence value) {
        return value.codePoints().noneMatch(PlainTextValidator::isForbidden);
    }

    private static boolean isForbidden(int c) {
        if (c == '\n' || c == '\r' || c == '\t') {
            return false;
        }
        return Character.isISOControl(c)
                || c == 0x200B                       // zero-width space
                || c == 0x2060 || c == 0xFEFF        // word joiner, BOM
                || c >= 0x202A && c <= 0x202E        // bidi embedding/override
                || c >= 0x2066 && c <= 0x2069        // bidi isolate
                || c >= 0xD800 && c <= 0xDFFF;       // yolg'iz surrogate
    }
}
