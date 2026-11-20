package uz.usta.platform.shared.error;

/** Validatsiya xatoligidagi bitta maydon. */
public record FieldViolation(String field, String message) {
}
