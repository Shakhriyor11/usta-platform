package uz.usta.platform.profile.internal.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.usta.platform.profile.internal.domain.Address;
import uz.usta.platform.profile.internal.domain.CustomerProfile;
import uz.usta.platform.profile.internal.domain.MasterProfile;
import uz.usta.platform.profile.internal.domain.VerificationDocument;
import uz.usta.platform.profile.internal.domain.VerificationStatus;
import uz.usta.platform.profile.internal.web.ProfileDtos.AddressResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.CustomerProfileResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.DocumentResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.MasterProfileResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.PublicMasterResponse;
import uz.usta.platform.shared.web.GeoPoint;
import uz.usta.platform.storage.FileStorage;

/** Entity -> DTO. Fayl URL'lari (presigned) {@link FileStorage} orqali hosil qilinadi. */
@Mapper(uses = FileUrls.class, imports = {GeoPoint.class, VerificationStatus.class})
interface ProfileWebMapper {

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "avatarUrl", source = "avatarKey", qualifiedByName = "fileUrl")
    CustomerProfileResponse toResponse(CustomerProfile profile);

    @Mapping(target = "location", expression = "java(new GeoPoint(a.getLatitude(), a.getLongitude()))")
    @Mapping(target = "isDefault", source = "defaultAddress")
    AddressResponse toResponse(Address a);

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "avatarUrl", source = "avatarKey", qualifiedByName = "fileUrl")
    @Mapping(target = "workLocation", expression = "java(workLocation(m))")
    @Mapping(target = "verified", expression = "java(m.getVerificationStatus() == VerificationStatus.APPROVED)")
    MasterProfileResponse toResponse(MasterProfile m);

    @Mapping(target = "avatarUrl", source = "avatarKey", qualifiedByName = "fileUrl")
    @Mapping(target = "verified", constant = "true")
    PublicMasterResponse toPublic(MasterProfile m);

    @Mapping(target = "url", source = "fileKey", qualifiedByName = "fileUrl")
    DocumentResponse toResponse(VerificationDocument d);

    default GeoPoint workLocation(MasterProfile m) {
        return m.hasWorkArea() ? new GeoPoint(m.getWorkLatitude(), m.getWorkLongitude()) : null;
    }
}
