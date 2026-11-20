package uz.usta.platform.profile.internal.web;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;
import uz.usta.platform.storage.FileStorage;

@Component
class FileUrls {

    private final FileStorage storage;

    FileUrls(FileStorage storage) {
        this.storage = storage;
    }

    @Named("fileUrl")
    String url(String key) {
        return key == null ? null : storage.url(key);
    }
}
