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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

            // 从路径中提取文件名
            String fileName = file.getName();

            // 设置Content-Disposition，确保浏览器下载时使用正确的文件名
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
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
            log.info("接收到比对记录请求: originalFilename={}, targetFilename={}, originalFilePath={}, targetFilePath={}",
                record.getOriginalFilename(), record.getTargetFilename(),
                record.getOriginalFilePath(), record.getTargetFilePath());

            // 设置批次ID
            if (record.getBatchId() == null || record.getBatchId().isEmpty()) {
                record.setBatchId(IdUtil.simpleUUID());
            }

            // 设置操作时间
            record.setCreateTime(LocalDateTime.now());


            // 插入记录
            log.info("准备插入比对记录: {}", record);
            int result = recordMapper.insert(record);

            if (result > 0) {
                log.info("比对日志记录成功, batchId: {}, originalFilename: {}, targetFilename: {}",
                    record.getBatchId(), record.getOriginalFilename(), record.getTargetFilename());
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
     * 获取比对历史记录
     * @param page 页码
     * @param size 每页大小
     * @param filename 文件名称搜索
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 比对记录列表
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String filename,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            // 计算偏移量
            int offset = (page - 1) * size;

            // 查询比对记录列表，按批次ID分组，支持搜索
            List<Map<String, Object>> history = recordMapper.selectHistoryGroupByBatch(offset, size, filename, startDate, endDate);

            // 查询总数，支持搜索
            Long total = recordMapper.selectHistoryCount(filename, startDate, endDate);

            Map<String, Object> result = new HashMap<>();
            result.put("records", history);
            result.put("total", total);
            result.put("current", page);
            result.put("size", size);
            result.put("pages", (total + size - 1) / size);

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取比对历史记录失败", e);
            return Result.error("获取历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取比对详情
     * @param batchId 批次ID
     * @return 比对详情
     */
    @GetMapping("/history/{batchId}")
    public Result<List<ComparisonRecord>> getHistoryDetail(@PathVariable String batchId) {
        try {
            log.info("查询比对详情, batchId: {}", batchId);
            List<ComparisonRecord> details = recordMapper.selectByBatchId(batchId);
            log.info("查询到比对详情记录数: {}", details.size());
            for (ComparisonRecord record : details) {
                log.info("记录详情: batchId={}, originalFilename={}, targetFilename={}",
                    record.getBatchId(), record.getOriginalFilename(), record.getTargetFilename());
            }
            return Result.success(details);
        } catch (Exception e) {
            log.error("获取比对详情失败, batchId: {}", batchId, e);
            return Result.error("获取比对详情失败: " + e.getMessage());
        }
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