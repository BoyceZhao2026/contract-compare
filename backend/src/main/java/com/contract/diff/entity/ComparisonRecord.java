package com.contract.diff.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comparison_records")
public class ComparisonRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private String batchId;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("original_file_path")
    private String originalFilePath;

    @TableField("target_filename")
    private String targetFilename;

    @TableField("target_file_path")
    private String targetFilePath;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}