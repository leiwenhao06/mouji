package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.ImageDAO;
import com.eyefollowup.ledger.dao.PatientDAO;
import com.eyefollowup.ledger.dto.ExamRequest;
import com.eyefollowup.ledger.dto.ImportConfirmRequest;
import com.eyefollowup.ledger.entity.BiomechanicalParams;
import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.MedicalImage;
import com.eyefollowup.ledger.entity.Patient;
import com.eyefollowup.ledger.gateway.OcrClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报告导入：上传图片 → OCR 提取 → 人工核对 → 建档/建检查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "bmp");

    private final OcrClient ocrClient;
    private final ObjectMapper mapper;
    private final ImageDAO imageDAO;
    private final PatientDAO patientDAO;
    private final ExamService examService;
    private final AuditService auditService;

    @Value("${app.storage.image-dir:./data/images}")
    private String imageDir;

    /**
     * 第一步：保存图片并调用 OCR，返回可编辑的预览数据（不建检查）。
     */
    public Map<String, Object> preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择报告图片");
        }
        String original = file.getOriginalFilename() == null ? "report.jpg" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("仅支持 jpg/jpeg/png/bmp 格式的报告图片");
        }

        Path dir = Paths.get(imageDir).toAbsolutePath().normalize();
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new BizException(500, "保存图片失败：" + e.getMessage());
        }

        JsonNode ocr;
        try {
            ocr = ocrClient.analyze(target.toString());
        } catch (BizException e) {
            // 保留文件，但明确告知 OCR 不可用；前端可转手动录入
            throw e;
        }

        Map<String, Object> flat = flatten(ocr.path("fields"));
        Map<String, Object> rejected = flatten(ocr.path("rejected"));
        String ocrJson = ocr.toString();

        MedicalImage image = new MedicalImage();
        image.setImageType("Pentacam");
        image.setEyeSide(normalizeEyeSide(str(flat.get("eye_side"))));
        image.setImagePath("data/images/" + storedName);
        image.setFileName(original);
        image.setFileSize(file.getSize());
        image.setOcrJson(ocrJson);
        image.setReportType(ocr.path("image_type").asText("Pentacam_4Maps"));
        image.setOcrConfidence(BigDecimal.valueOf(ocr.path("confidence").asDouble(0)));
        imageDAO.insert(image);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imageId", image.getImageId());
        result.put("imagePath", image.getImagePath());
        result.put("fileName", original);
        result.put("reportType", image.getReportType());
        result.put("confidence", ocr.path("confidence").asDouble(0));
        result.put("fields", flat);
        result.put("rejected", rejected);
        result.put("suggestedPatientName", str(flat.get("patient_name")));
        result.put("suggestedGender", normalizeGender(str(flat.get("gender"))));
        result.put("suggestedDateOfBirth", parseDate(str(flat.get("date_of_birth"))));
        result.put("suggestedExamDate", parseDate(str(flat.get("exam_date"))));
        result.put("suggestedExamTime", parseTime(str(flat.get("exam_time"))));
        result.put("suggestedEyeSide", normalizeEyeSide(str(flat.get("eye_side"))));
        result.put("topography", mapTopography(flat));
        result.put("biomechanics", mapBiomechanics(flat));
        result.put("ocrJson", ocrJson);
        return result;
    }

    /**
     * 第二步：确认预览数据 → 匹配/新建患者 → 建检查 → 关联图片。
     * 检查入库后自动触发进展检测与随访计划调整。
     */
    @Transactional
    public Map<String, Object> confirm(ImportConfirmRequest req) {
        if (req.getImageId() == null) {
            throw new BizException("导入会话无效，请重新上传报告");
        }
        MedicalImage image = imageDAO.findById(req.getImageId());
        if (image == null) {
            throw new BizException(404, "导入会话不存在或已过期");
        }

        Patient patient;
        if (req.getPatientId() != null && !req.getPatientId().isBlank()) {
            patient = patientDAO.findById(req.getPatientId());
            if (patient == null) {
                throw new BizException(404, "所选患者不存在");
            }
        } else {
            String name = req.getName() == null ? null : req.getName().trim();
            if (name == null || name.isEmpty()) {
                throw new BizException("请填写患者姓名，或选择已有患者");
            }
            patient = req.getDateOfBirth() == null ? null
                    : patientDAO.findByNameAndBirth(name, req.getDateOfBirth());
            if (patient == null) {
                patient = new Patient();
                patient.setPatientId(UUID.randomUUID().toString().replace("-", ""));
                patient.setName(name);
                patient.setGender(req.getGender());
                patient.setDateOfBirth(req.getDateOfBirth());
                patient.setAge(PatientService.calcAge(req.getDateOfBirth()));
                patient.setMedicalHistory(null);
                patientDAO.insert(patient);
            }
        }

        // OCR 提取的全量字段作为底稿，用户在预览页确认/修改的值覆盖其上，
        // 避免只入库表单展示的少数指标、丢失后表面参数与眼前节数据。
        Map<String, Object> ocrFlat = storedOcrFields(image.getOcrJson());
        ExamRequest examRequest = new ExamRequest();
        examRequest.setPatientId(patient.getPatientId());
        examRequest.setExamDate(req.getExamDate() == null ? LocalDate.now() : req.getExamDate());
        examRequest.setExamTime(req.getExamTime());
        examRequest.setEyeSide(req.getEyeSide() == null ? "right" : req.getEyeSide());
        examRequest.setExamType(req.getExamType() == null ? "Pentacam" : req.getExamType());
        examRequest.setNotes(req.getNotes() == null ? "报告导入" : req.getNotes());
        examRequest.setTopography(mergeTopography(ocrFlat, req.getTopography()));
        examRequest.setBiomechanics(mergeBiomechanics(ocrFlat, req.getBiomechanics()));

        FollowupExam exam = examService.create(examRequest);

        imageDAO.attach(image.getImageId(), patient.getPatientId(), exam.getExamId(),
                examRequest.getEyeSide(),
                req.getOcrJson() == null ? image.getOcrJson() : req.getOcrJson(),
                image.getReportType(), image.getOcrConfidence());

        auditService.log("IMPORT", "CONFIRM",
                "报告导入确认：图片 #" + image.getImageId() + " → 检查 #" + exam.getExamId(),
                patient.getPatientId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientId", patient.getPatientId());
        result.put("patientName", patient.getName());
        result.put("examId", exam.getExamId());
        result.put("riskFlag", exam.getRiskFlag());
        result.put("stageCode", exam.getStageCode());
        return result;
    }

    // ---------------- OCR 字段映射 ----------------

    private Map<String, Object> flatten(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isObject() && v.has("value")) {
                map.put(e.getKey(), jsonToJava(v.get("value")));
            } else {
                map.put(e.getKey(), jsonToJava(v));
            }
        });
        return map;
    }

    private Object jsonToJava(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        if (v.isTextual()) return v.asText();
        if (v.isNumber()) return v.decimalValue();
        if (v.isBoolean()) return v.asBoolean();
        return v.toString();
    }

    private CornealTopography mapTopography(Map<String, Object> f) {
        CornealTopography t = new CornealTopography();
        t.setFrontK1(bd(f, "front_k1"));
        t.setFrontK2(bd(f, "front_k2"));
        t.setFrontKm(bd(f, "front_km"));
        t.setFrontKmax(bd(f, "front_kmax"));
        t.setFrontRmin(bd(f, "front_rmin"));
        t.setFrontQVal(bd(f, "front_q_val"));
        t.setFrontAxis(bd(f, "front_axis"));
        t.setFrontAstig(bd(f, "front_astig"));
        t.setFrontQs(str(f.get("front_qs")));
        t.setBackK1(bd(f, "back_k1"));
        t.setBackK2(bd(f, "back_k2"));
        t.setBackKm(bd(f, "back_km"));
        t.setBackRmin(bd(f, "back_rmin"));
        t.setBackQVal(bd(f, "back_q_val"));
        t.setBackQs(str(f.get("back_qs")));
        t.setPupilCenterPachy(bd(f, "pupil_center_pachy"));
        t.setPachyApex(bd(f, "pachy_apex"));
        t.setThinnestPachy(bd(f, "thinnest_pachy"));
        t.setCorneaVolume(bd(f, "cornea_volume"));
        t.setAcDepth(bd(f, "ac_depth"));
        t.setIop(bd(f, "iop"));
        return t;
    }

    private BiomechanicalParams mapBiomechanics(Map<String, Object> f) {
        BiomechanicalParams b = new BiomechanicalParams();
        b.setCcbi(bd(f, "ccbi"));
        b.setCtbi(bd(f, "ctbi"));
        b.setIsValue(bd(f, "is_value"));
        b.setSpA1(bd(f, "sp_a1"));
        b.setIntegrRadius(bd(f, "integr_radius"));
        b.setArth(bd(f, "arth"));
        b.setDaRatio(bd(f, "da_ratio"));
        b.setSsi(bd(f, "ssi"));
        return b;
    }

    /** 解析入库图片上保存的 OCR 原始结果，取出扁平字段（上传时已存全量 OCR JSON）。 */
    private Map<String, Object> storedOcrFields(String ocrJson) {
        if (ocrJson == null || ocrJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = mapper.readTree(ocrJson);
            JsonNode fields = root.has("fields") ? root.get("fields") : root;
            return flatten(fields);
        } catch (Exception e) {
            log.warn("OCR JSON 解析失败，仅使用用户确认值: {}", e.getMessage());
            return Map.of();
        }
    }

    /** OCR 底稿 + 用户确认值合并：用户非空值优先。 */
    private CornealTopography mergeTopography(Map<String, Object> ocrFlat, CornealTopography override) {
        CornealTopography t = mapTopography(ocrFlat);
        if (override == null) {
            return t;
        }
        if (override.getFrontK1() != null) t.setFrontK1(override.getFrontK1());
        if (override.getFrontK2() != null) t.setFrontK2(override.getFrontK2());
        if (override.getFrontKm() != null) t.setFrontKm(override.getFrontKm());
        if (override.getFrontKmax() != null) t.setFrontKmax(override.getFrontKmax());
        if (override.getFrontRmin() != null) t.setFrontRmin(override.getFrontRmin());
        if (override.getFrontQVal() != null) t.setFrontQVal(override.getFrontQVal());
        if (override.getFrontAxis() != null) t.setFrontAxis(override.getFrontAxis());
        if (override.getFrontAstig() != null) t.setFrontAstig(override.getFrontAstig());
        if (override.getFrontQs() != null) t.setFrontQs(override.getFrontQs());
        if (override.getBackK1() != null) t.setBackK1(override.getBackK1());
        if (override.getBackK2() != null) t.setBackK2(override.getBackK2());
        if (override.getBackKm() != null) t.setBackKm(override.getBackKm());
        if (override.getBackRmin() != null) t.setBackRmin(override.getBackRmin());
        if (override.getBackQVal() != null) t.setBackQVal(override.getBackQVal());
        if (override.getBackQs() != null) t.setBackQs(override.getBackQs());
        if (override.getPupilCenterPachy() != null) t.setPupilCenterPachy(override.getPupilCenterPachy());
        if (override.getPachyApex() != null) t.setPachyApex(override.getPachyApex());
        if (override.getThinnestPachy() != null) t.setThinnestPachy(override.getThinnestPachy());
        if (override.getCorneaVolume() != null) t.setCorneaVolume(override.getCorneaVolume());
        if (override.getAcDepth() != null) t.setAcDepth(override.getAcDepth());
        if (override.getIop() != null) t.setIop(override.getIop());
        return t;
    }

    private BiomechanicalParams mergeBiomechanics(Map<String, Object> ocrFlat, BiomechanicalParams override) {
        BiomechanicalParams b = mapBiomechanics(ocrFlat);
        if (override == null) {
            return b;
        }
        if (override.getCcbi() != null) b.setCcbi(override.getCcbi());
        if (override.getCtbi() != null) b.setCtbi(override.getCtbi());
        if (override.getIsValue() != null) b.setIsValue(override.getIsValue());
        if (override.getSpA1() != null) b.setSpA1(override.getSpA1());
        if (override.getIntegrRadius() != null) b.setIntegrRadius(override.getIntegrRadius());
        if (override.getArth() != null) b.setArth(override.getArth());
        if (override.getDaRatio() != null) b.setDaRatio(override.getDaRatio());
        if (override.getSsi() != null) b.setSsi(override.getSsi());
        return b;
    }

    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private BigDecimal bd(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        Matcher m = NUMBER.matcher(String.valueOf(v));
        if (!m.find()) return null;
        try {
            return new BigDecimal(m.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private String normalizeGender(String g) {
        if (g == null) return null;
        String s = g.trim().toLowerCase();
        if (s.startsWith("m") || s.contains("男")) return "Male";
        if (s.startsWith("f") || s.contains("女")) return "Female";
        return "Other";
    }

    private String normalizeEyeSide(String e) {
        if (e == null) return null;
        String s = e.trim().toLowerCase();
        if (s.contains("right") || s.equals("od") || s.contains("右")) return "right";
        if (s.contains("left") || s.equals("os") || s.contains("左")) return "left";
        if (s.contains("both") || s.contains("双")) return "both";
        return null;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().replace('.', '-').replace('/', '-');
        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
        }
        for (String pattern : new String[]{"yyyy-M-d", "d-M-yyyy", "M-d-yyyy", "yyyyMMdd"}) {
            try {
                return LocalDate.parse(v, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        for (String pattern : new String[]{"H:mm:ss", "H:mm", "HH:mm:ss", "HH:mm"}) {
            try {
                return LocalTime.parse(v, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
