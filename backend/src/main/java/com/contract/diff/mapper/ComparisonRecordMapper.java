package com.contract.diff.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contract.diff.entity.ComparisonRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ComparisonRecordMapper extends BaseMapper<ComparisonRecord> {

    /**
     * 查询比对历史记录，按批次ID分组，支持搜索
     * @param offset 偏移量
     * @param size 每页大小
     * @param filename 文件名称搜索
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 历史记录列表
     */
    @Select("<script>" +
            "SELECT " +
            "  batch_id, " +
            "  MIN(create_time) as create_time, " +
            "  GROUP_CONCAT(DISTINCT original_filename ORDER BY original_filename) as original_filenames, " +
            "  GROUP_CONCAT(DISTINCT target_filename ORDER BY target_filename) as target_filenames " +
            "FROM comparison_records " +
            "WHERE 1=1 " +
            "<if test='filename != null and filename != \"\"'>" +
            "  AND (original_filename LIKE CONCAT('%', #{filename}, '%') OR target_filename LIKE CONCAT('%', #{filename}, '%')) " +
            "</if>" +
            "<if test='startDate != null and startDate != \"\"'>" +
            "  AND DATE(create_time) &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            "  AND DATE(create_time) &lt;= #{endDate} " +
            "</if>" +
            "GROUP BY batch_id " +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<Map<String, Object>> selectHistoryGroupByBatch(
            @Param("offset") Integer offset,
            @Param("size") Integer size,
            @Param("filename") String filename,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * 查询历史记录总数，支持搜索
     * @param filename 文件名称搜索
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 总数
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT batch_id) FROM comparison_records " +
            "WHERE 1=1 " +
            "<if test='filename != null and filename != \"\"'>" +
            "  AND (original_filename LIKE CONCAT('%', #{filename}, '%') OR target_filename LIKE CONCAT('%', #{filename}, '%')) " +
            "</if>" +
            "<if test='startDate != null and startDate != \"\"'>" +
            "  AND DATE(create_time) &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            "  AND DATE(create_time) &lt;= #{endDate} " +
            "</if>" +
            "</script>")
    Long selectHistoryCount(
            @Param("filename") String filename,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * 根据批次ID查询比对记录
     * @param batchId 批次ID
     * @return 比对记录列表
     */
    @Select("SELECT * FROM comparison_records WHERE batch_id = #{batchId} ORDER BY id")
    List<ComparisonRecord> selectByBatchId(@Param("batchId") String batchId);
}