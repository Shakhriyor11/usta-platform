package uz.usta.platform.shared.openapi;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import uz.usta.platform.shared.security.AdminPermission;

/** {@code @PreAuthorize} bilan qo'shimcha {@code DELETE} huquqi talab qiladigan amallarni belgilaydi. */
@Component
class DeletePermissionCustomizer implements GlobalOperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod method) {
        PreAuthorize rule = method.getMethodAnnotation(PreAuthorize.class);
        if (rule != null && rule.value().contains(AdminPermission.DELETE.authority())) {
            operation.addExtension(ApiDocsCustomizer.NEEDS_DELETE, true);
        }
        return operation;
    }
}
