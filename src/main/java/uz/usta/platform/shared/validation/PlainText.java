package uz.usta.platform.shared.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Oddiy matn: boshqaruv belgilari (yangi qator va tabdan tashqari) hamda matnni ko'rinishda aldaydigan
 * ko'rinmas/yo'nalishni o'zgartiruvchi belgilar (bidi override, zero-width) taqiqlangan.
 */
@Documented
@Constraint(validatedBy = PlainTextValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface PlainText {

    String message() default "{validation.plain-text}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
