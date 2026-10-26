package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.common.PasswordPolicy;
import com.eyefollowup.ledger.dao.UserDAO;
import com.eyefollowup.ledger.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 个人资料：昵称与头像。
 * 头像文件保存在 data/avatars（随 data 目录一起被 .gitignore 排除）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024L;

    private final UserDAO userDAO;
    private final AuditService auditService;

    @Value("${app.storage.avatar-dir:./data/avatars}")
    private String avatarDir;

    public Map<String, Object> profile(String username) {
        return toProfile(requireUser(username));
    }

    public Map<String, Object> updateDisplayName(String username, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new BizException("昵称不能为空");
        }
        String value = displayName.trim();
        if (value.length() > 50) {
            throw new BizException("昵称最多 50 个字符");
        }
        requireUser(username);
        userDAO.updateDisplayName(username, value);
        auditService.log("USER", "UPDATE_PROFILE", "修改昵称", null);
        return profile(username);
    }

    public Map<String, Object> uploadAvatar(String username, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BizException("头像图片不能超过 2MB");
        }
        String original = file.getOriginalFilename() == null ? "avatar.jpg" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("头像仅支持 jpg / png / webp 格式");
        }
        byte[] head = readHead(file, 12);
        if (!hasImageMagic(head, ext)) {
            throw new BizException("文件内容与图片格式不符，请上传真实图片");
        }

        User user = requireUser(username);
        Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new BizException(500, "头像保存失败：" + e.getMessage());
        }

        userDAO.updateAvatar(username, storedName);
        deleteOldAvatar(user.getAvatarPath());
        auditService.log("USER", "UPDATE_AVATAR", "更新头像", null);
        return profile(username);
    }

    /** 修改密码：校验旧密码，新密码要求 ≥8 位且同时包含字母与数字。 */
    public void changePassword(String username, String oldPassword, String newPassword) {
        PasswordPolicy.validate(newPassword);
        if (newPassword.equals(oldPassword)) {
            throw new BizException("新密码不能与原密码相同");
        }
        User user = requireUser(username);
        if (oldPassword == null || !BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            throw new BizException(400, "原密码不正确");
        }
        userDAO.updatePassword(user.getId(), BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        auditService.log("USER", "CHANGE_PASSWORD", "修改登录密码", null);
    }

    public AvatarFile loadAvatar(String username) {
        User user = requireUser(username);
        String storedName = user.getAvatarPath();
        if (storedName == null || storedName.isBlank()) {
            throw new BizException(404, "尚未设置头像");
        }
        // 仅允许读取头像目录下的文件名，避免路径穿越
        String fileName = Paths.get(storedName).getFileName().toString();
        Path path = Paths.get(avatarDir).toAbsolutePath().normalize().resolve(fileName).normalize();
        if (!path.startsWith(Paths.get(avatarDir).toAbsolutePath().normalize()) || !Files.exists(path)) {
            throw new BizException(404, "头像文件不存在");
        }
        try {
            String lower = fileName.toLowerCase();
            String contentType = lower.endsWith(".png") ? "image/png"
                    : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
            return new AvatarFile(Files.readAllBytes(path), contentType);
        } catch (Exception e) {
            throw new BizException(404, "头像文件读取失败");
        }
    }

    /** 读取文件头部字节，用于魔数校验。 */
    private byte[] readHead(MultipartFile file, int n) {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[n];
            int read = 0;
            while (read < n) {
                int r = in.read(buf, read, n - read);
                if (r < 0) {
                    break;
                }
                read += r;
            }
            return read == n ? buf : java.util.Arrays.copyOf(buf, read);
        } catch (Exception e) {
            throw new BizException("头像文件读取失败");
        }
    }

    /**
     * 魔数校验：JPEG=FF D8 FF，PNG=89 50 4E 47，WebP=RIFF????WEBP。
     * 防止把可执行文件/脚本改后缀伪装成图片上传。
     */
    static boolean hasImageMagic(byte[] head, String ext) {
        if (head == null || head.length < 4) {
            return false;
        }
        int b0 = head[0] & 0xFF, b1 = head[1] & 0xFF, b2 = head[2] & 0xFF, b3 = head[3] & 0xFF;
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "jpg".equals(ext) || "jpeg".equals(ext);
        }
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "png".equals(ext);
        }
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 && head.length >= 12
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp".equals(ext);
        }
        return false;
    }

    private void deleteOldAvatar(String oldName) {
        if (oldName == null || oldName.isBlank()) {
            return;
        }
        try {
            String fileName = Paths.get(oldName).getFileName().toString();
            Path old = Paths.get(avatarDir).toAbsolutePath().normalize().resolve(fileName).normalize();
            Files.deleteIfExists(old);
        } catch (Exception e) {
            log.warn("旧头像删除失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> toProfile(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("displayName", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
        map.put("role", user.getRole());
        map.put("avatarPath", user.getAvatarPath());
        return map;
    }

    private User requireUser(String username) {
        User user = userDAO.findByUsername(username);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    public record AvatarFile(byte[] data, String contentType) {
    }
}
