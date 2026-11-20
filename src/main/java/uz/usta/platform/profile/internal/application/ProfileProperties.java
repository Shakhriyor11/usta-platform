package uz.usta.platform.profile.internal.application;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import uz.usta.platform.profile.internal.domain.DocumentType;

/**
 * @param requiredDocuments verifikatsiyaga yuborish uchun majburiy hujjat turlari
 */
@ConfigurationProperties("app.profile")
public record ProfileProperties(Set<DocumentType> requiredDocuments, int maxDocuments, int maxAddresses) {

    public ProfileProperties {
        requiredDocuments = requiredDocuments == null
                ? Set.of(DocumentType.PASSPORT, DocumentType.SELFIE) : Set.copyOf(requiredDocuments);
        maxDocuments = maxDocuments <= 0 ? 10 : maxDocuments;
        maxAddresses = maxAddresses <= 0 ? 10 : maxAddresses;
    }
}
