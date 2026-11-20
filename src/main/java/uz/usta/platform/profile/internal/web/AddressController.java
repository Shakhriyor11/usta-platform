package uz.usta.platform.profile.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.profile.internal.application.AddressService;
import uz.usta.platform.profile.internal.web.ProfileDtos.AddressRequest;
import uz.usta.platform.profile.internal.web.ProfileDtos.AddressResponse;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Customer: Addresses", description = "Mijoz manzillari (CUSTOMER roli)")
@RestController
@RequestMapping(ApiPaths.CUSTOMER + "/addresses")
class AddressController {

    private final AddressService service;
    private final ProfileWebMapper mapper;

    AddressController(AddressService service, ProfileWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Manzillar ro'yxati")
    @GetMapping
    List<AddressResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id()).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Manzil")
    @GetMapping("/{id}")
    AddressResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return mapper.toResponse(service.get(user.id(), id));
    }

    @Operation(summary = "Manzil qo'shish", description = "Birinchi manzil avtomatik asosiy bo'ladi")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AddressResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AddressRequest body) {
        return mapper.toResponse(service.create(user.id(), command(body)));
    }

    @Operation(summary = "Manzilni tahrirlash")
    @PutMapping("/{id}")
    AddressResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                           @Valid @RequestBody AddressRequest body) {
        return mapper.toResponse(service.update(user.id(), id, command(body)));
    }

    @Operation(summary = "Asosiy manzil qilish")
    @PostMapping("/{id}/default")
    AddressResponse makeDefault(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return mapper.toResponse(service.makeDefault(user.id(), id));
    }

    @Operation(summary = "Manzilni o'chirish")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
    }

    private static AddressService.AddressCommand command(AddressRequest r) {
        return new AddressService.AddressCommand(r.title(), r.address(), r.details(), r.location().latitude(),
                r.location().longitude(), Boolean.TRUE.equals(r.isDefault()));
    }
}
