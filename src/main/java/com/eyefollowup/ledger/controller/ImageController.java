package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.ImageDAO;
import com.eyefollowup.ledger.entity.MedicalImage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 报告影像访问（需登录；不提供匿名访问）。
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageDAO imageDAO;

    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> get(@PathVariable int imageId) {
        MedicalImage image = imageDAO.findById(imageId);
        if (image == null || image.getImagePath() == null) {
            throw new BizException(404, "影像不存在");
        }
        try {
            Path path = Paths.get(image.getImagePath()).toAbsolutePath().normalize();
            byte[] bytes = Files.readAllBytes(path);
            String lower = image.getImagePath().toLowerCase();
            MediaType type = lower.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(type).body(bytes);
        } catch (Exception e) {
            throw new BizException(404, "影像文件不存在");
        }
    }

    @GetMapping("/patient/{patientId}")
    public ApiResponse<List<MedicalImage>> listByPatient(@PathVariable String patientId) {
        return ApiResponse.ok(imageDAO.listByPatient(patientId));
    }

    @GetMapping("/exam/{examId}")
    public ApiResponse<List<MedicalImage>> listByExam(@PathVariable int examId) {
        return ApiResponse.ok(imageDAO.listByExam(examId));
    }
}
