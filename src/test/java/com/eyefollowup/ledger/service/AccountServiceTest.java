package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.AuditDAO;
import com.eyefollowup.ledger.dao.UserDAO;
import com.eyefollowup.ledger.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人资料服务的纯单元测试（不依赖 Spring 上下文与数据库）。
 */
class AccountServiceTest {

    private UserDAO userDAO;
    private AccountService service;

    @TempDir
    Path avatarDir;

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @BeforeEach
    void setUp() {
        userDAO = mock(UserDAO.class);
        AuditService auditService = new AuditService(mock(AuditDAO.class));
        service = new AccountService(userDAO, auditService);
        ReflectionTestUtils.setField(service, "avatarDir", avatarDir.toString());
    }

    private User user() {
        User u = new User();
        u.setId(1);
        u.setUsername("admin");
        u.setDisplayName("系统管理员");
        u.setRole("admin");
        u.setPasswordHash(BCrypt.hashpw("Admin@2026", BCrypt.gensalt()));
        return u;
    }

    @Test
    void displayNameBlankRejected() {
        when(userDAO.findByUsername("admin")).thenReturn(user());
        assertThrows(BizException.class, () -> service.updateDisplayName("admin", "   "));
        verify(userDAO, never()).updateDisplayName(anyString(), anyString());
    }

    @Test
    void displayNameTooLongRejected() {
        when(userDAO.findByUsername("admin")).thenReturn(user());
        assertThrows(BizException.class, () -> service.updateDisplayName("admin", "超".repeat(51)));
    }

    @Test
    void displayNameUpdated() {
        User u = user();
        when(userDAO.findByUsername("admin")).thenReturn(u);
        doAnswer(inv -> {
            u.setDisplayName(inv.getArgument(1));
            return null;
        }).when(userDAO).updateDisplayName(anyString(), anyString());
        var profile = service.updateDisplayName("admin", " 林医生 ");
        verify(userDAO).updateDisplayName("admin", "林医生");
        assertEquals("林医生", profile.get("displayName"));
    }

    @Test
    void avatarBadExtensionRejected() {
        when(userDAO.findByUsername("admin")).thenReturn(user());
        MockMultipartFile file = new MockMultipartFile("file", "evil.gif", "image/gif", PNG_HEAD);
        assertThrows(BizException.class, () -> service.uploadAvatar("admin", file));
        verify(userDAO, never()).updateAvatar(anyString(), anyString());
    }

    @Test
    void avatarFakeContentRejected() {
        when(userDAO.findByUsername("admin")).thenReturn(user());
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "not really a png".getBytes());
        BizException e = assertThrows(BizException.class, () -> service.uploadAvatar("admin", file));
        assertTrue(e.getMessage().contains("格式不符"));
        verify(userDAO, never()).updateAvatar(anyString(), anyString());
    }

    @Test
    void avatarUploadedAndOldRemoved() throws Exception {
        User u = user();
        u.setAvatarPath("old-avatar.png");
        Files.write(avatarDir.resolve("old-avatar.png"), PNG_HEAD);
        when(userDAO.findByUsername("admin")).thenReturn(u);
        // 注意：不能在此 stub 里回写 u.avatarPath —— 服务在 update 之后才读取旧路径做清理，
        // 真实 DAO 不会篡改内存中的实体快照。改用捕获方式拿新文件名。
        String[] newName = new String[1];
        doAnswer(inv -> {
            newName[0] = inv.getArgument(1);
            return null;
        }).when(userDAO).updateAvatar(anyString(), anyString());

        MockMultipartFile file = new MockMultipartFile("file", "new.png", "image/png", PNG_HEAD);
        service.uploadAvatar("admin", file);

        String stored = newName[0];
        assertTrue(stored.endsWith(".png"));
        assertArrayEquals(PNG_HEAD, Files.readAllBytes(avatarDir.resolve(stored)));
        assertTrue(Files.notExists(avatarDir.resolve("old-avatar.png")), "旧头像应被清理");
        verify(userDAO).updateAvatar(eq("admin"), eq(stored));
    }

    @Test
    void avatarLoadRejectsTraversal() {
        User u = user();
        u.setAvatarPath("../secret.png");
        when(userDAO.findByUsername("admin")).thenReturn(u);
        assertThrows(BizException.class, () -> service.loadAvatar("admin"));
    }

    @Test
    void passwordTooWeakRejected() {
        when(userDAO.findByUsername("admin")).thenReturn(user());
        assertThrows(BizException.class, () -> service.changePassword("admin", "Admin@2026", "onlyletters"));
        assertThrows(BizException.class, () -> service.changePassword("admin", "Admin@2026", "12345678"));
        assertThrows(BizException.class, () -> service.changePassword("admin", "Admin@2026", "ab1"));
    }

    @Test
    void passwordWrongOldRejected() {
        when(userDAO.findByUsername("admin")).thenReturn(user());
        BizException e = assertThrows(BizException.class,
                () -> service.changePassword("admin", "wrong-old", "NewPass123"));
        assertTrue(e.getMessage().contains("原密码不正确"));
        verify(userDAO, never()).updatePassword(eq(1), anyString());
    }

    @Test
    void passwordChanged() {
        when(userDAO.findByUsername("admin")).thenReturn(user());
        service.changePassword("admin", "Admin@2026", "NewPass123");
        verify(userDAO).updatePassword(eq(1), org.mockito.ArgumentMatchers.argThat(
                hash -> hash != null && !hash.equals("NewPass123") && BCrypt.checkpw("NewPass123", hash)));
    }

    @Test
    void magicNumberChecks() {
        assertTrue(AccountService.hasImageMagic(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x11}, "jpg"));
        assertTrue(AccountService.hasImageMagic(PNG_HEAD, "png"));
        assertTrue(AccountService.hasImageMagic(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, "webp"));
        // 扩展名与内容不匹配
        assertTrue(!AccountService.hasImageMagic(PNG_HEAD, "jpg"));
        assertTrue(!AccountService.hasImageMagic(new byte[]{1, 2, 3, 4}, "png"));
        assertTrue(!AccountService.hasImageMagic(new byte[]{1, 2}, "png"));
    }
}
