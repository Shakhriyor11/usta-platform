package uz.usta.platform.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Faqat testlar uchun: har xil xatolik turlarini chaqiradi. */
@RestController
@RequestMapping("/api/v1/public/test-errors")
class ErrorTestController {

    record SampleRequest(@NotBlank @Pattern(regexp = "\\+998\\d{9}") String phone, @Min(1) @Max(5) int rating) {
    }

    @PostMapping("/validate")
    Map<String, String> validate(@Valid @RequestBody SampleRequest request) {
        return Map.of("phone", request.phone());
    }

    @GetMapping("/param")
    Map<String, Integer> param(@RequestParam @Min(1) int page) {
        return Map.of("page", page);
    }

    @GetMapping("/business")
    void business() {
        throw BusinessException.notFound("Buyurtma", "42");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new DataIntegrityViolationException("duplicate key");
    }

    @GetMapping("/lock")
    void lock() {
        throw new ObjectOptimisticLockingFailureException("Order", "42");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("secret internal detail");
    }
}
