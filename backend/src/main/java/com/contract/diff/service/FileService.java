package com.contract.diff.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Date;

@Slf4j
@Service
public class FileService {

    @Value("${app.storage.location}")
    private String baseDir;

    /**
     * 存储上传的文件
     * @param file 上传的文件
     * @return 相对路径
     * @throws IOException IO异常
     */
    public String storeFile(MultipartFile file) throws IOException {
        // 验证文件类型
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".docx") && !originalFilename.toLowerCase().endsWith(".doc"))) {
            throw new IllegalArgumentException("仅支持.docx或.doc格式的文件");
        }

        // 创建日期目录
        String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
        File dir = new File(baseDir, datePath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("无法创建存储目录: " + dir.getAbsolutePath());
            }
        }

        // 生成唯一文件名
        String fileExtension = FileUtil.getSuffix(originalFilename);
        String fileName = IdUtil.simpleUUID() + "." + fileExtension;
        File dest = new File(dir, fileName);

        // 存储文件
        file.transferTo(dest);

        String relativePath = datePath + "/" + fileName;
        log.info("文件存储成功: {}, 大小: {} bytes", relativePath, file.getSize());

        return relativePath;
    }

    /**
     * 获取文件对象（含安全校验）
     * @param relativePath 相对路径
     * @return 文件对象
     * @throws IOException IO异常
     */
    public File getFile(String relativePath) throws IOException {
        File file = new File(baseDir, relativePath);

        // 安全检查：防止路径遍历攻击
        File baseFile = new File(baseDir);
        if (!FileUtil.isSub(baseFile, file)) {
            throw new SecurityException("非法路径访问: " + relativePath);
        }

        if (!file.exists()) {
            throw new IOException("文件不存在: " + relativePath);
        }

        return file;
    }

    /**
     * 初始化存储目录
     */
    public void initStorageDirectory() {
        File dir = new File(baseDir);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("存储目录初始化成功: {}", baseDir);
            } else {
                log.error("存储目录初始化失败: {}", baseDir);
            }
        }
    }
}