package com.contract.diff.controller;

import cn.hutool.core.util.IdUtil;
import com.contract.diff.common.Result;
import com.contract.diff.entity.ComparisonRecord;
import com.contract.diff.mapper.ComparisonRecordMapper;
import com.contract.diff.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/contract")
@CrossOrigin(origins = "*")
public class ContractController {

    @Autowired
    private FileService fileService;

    @Autowired
    private ComparisonRecordMapper recordMapper;

    /**
     * 文件上传接口
     * @param file 上传的文件
     * @return 文件路径和名称
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 验证文件大小
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            if (file.getSize() > 50 * 1024 * 1024) { // 50MB
                return Result.error("文件大小不能超过50MB");
            }

            // 存储文件
            String path = fileService.storeFile(file);

            Map<String, String> result = new HashMap<>();
            result.put("path", path);
            result.put("name", file.getOriginalFilename());
            result.put("size", String.valueOf(file.getSize()));

            log.info("文件上传成功: {}", file.getOriginalFilename());

            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件流接口
     * @param path 文件相对路径
     * @return 文件流
     */
    @GetMapping("/file/stream")
    public ResponseEntity<Resource> getFileStream(@RequestParam("path") String path) {
        try {
            File file = fileService.getFile(path);
            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (SecurityException e) {
            log.warn("非法路径访问: {}", path);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("文件读取失败: {}", path, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 记录比对日志
     * @param record 比对记录
     * @param request HTTP请求
     * @return 操作结果
     */
    @PostMapping("/record")
    public Result<Boolean> recordLog(@RequestBody ComparisonRecord record, HttpServletRequest request) {
        try {
            // 设置批次ID
            if (record.getBatchId() == null || record.getBatchId().isEmpty()) {
                record.setBatchId(IdUtil.simpleUUID());
            }

            // 设置操作时间
            record.setCreateTime(LocalDateTime.now());

            // 设置客户端IP
            String clientIp = getClientIpAddress(request);
            record.setClientIp(clientIp);

            // 插入记录
            int result = recordMapper.insert(record);

            if (result > 0) {
                log.info("比对日志记录成功, batchId: {}", record.getBatchId());
                return Result.success(true);
            } else {
                log.error("比对日志记录失败");
                return Result.error("记录保存失败");
            }
        } catch (Exception e) {
            log.error("记录比对日志异常", e);
            return Result.error("记录保存异常: " + e.getMessage());
        }
    }

    /**
     * 获取客户端IP地址
     * @param request HTTP请求
     * @return IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("服务运行正常");
    }
}