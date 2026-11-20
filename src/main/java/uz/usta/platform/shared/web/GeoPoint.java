package uz.usta.platform.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** WGS84 koordinata. */
public record GeoPoint(
        @Schema(example = "41.311081") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @Schema(example = "69.240562") @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
