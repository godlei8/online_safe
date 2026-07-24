package com.godlei.onlinesafe.auth.web;

import com.godlei.onlinesafe.auth.application.ProfileException;
import com.godlei.onlinesafe.auth.application.UserProfileService;
import com.godlei.onlinesafe.cos.LocalAvatarStorage;
import com.godlei.onlinesafe.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final LocalAvatarStorage localAvatarStorage;

    public UserProfileController(UserProfileService userProfileService, LocalAvatarStorage localAvatarStorage) {
        this.userProfileService = userProfileService;
        this.localAvatarStorage = localAvatarStorage;
    }

    @GetMapping
    public ProfileResponse getProfile(Authentication authentication) {
        return userProfileService.getProfile(requireUserId(authentication));
    }

    @PatchMapping("/username")
    public ProfileResponse changeUsername(
            @Valid @RequestBody ChangeUsernameRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        return userProfileService.changeUsername(
                requireUserId(authentication),
                request.username(),
                servletRequest,
                servletResponse
        );
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadAvatar(
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return userProfileService.uploadAvatar(requireUserId(authentication), file);
    }

    /** 本地 provider 下的头像文件；objectKey 形如 avatars/{userId}/{file} */
    @GetMapping("/avatar-file/{*objectKey}")
    public ResponseEntity<Resource> localAvatar(@PathVariable("objectKey") String objectKey) {
        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        Path path = localAvatarStorage.resolve(key);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        String lower = key.toLowerCase(Locale.ROOT);
        MediaType mediaType = lower.endsWith(".png") ? MediaType.IMAGE_PNG
                : lower.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }

    private static String requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ProfileException("UNAUTHENTICATED", "请先登录");
        }
        return principal.userId();
    }
}
