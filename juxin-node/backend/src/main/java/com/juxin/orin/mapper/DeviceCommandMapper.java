package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.dto.DeviceCommandGroup;
import com.juxin.orin.entity.DeviceCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceCommandMapper extends BaseMapper<DeviceCommand> {

    String GROUP_KEY_SQL = "MD5(CONCAT(command_type, '|', DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s'), "
            + "'|', SHA2(command_text, 256), '|', IFNULL(remark, '')))";

    @Select({
            "<script>",
            "SELECT",
            "  MIN(" + GROUP_KEY_SQL + ") AS groupKey,",
            "  command_type AS commandType,",
            "  MIN(command_text) AS commandText,",
            "  MIN(remark) AS remark,",
            "  MIN(command_no) AS sampleCommandNo,",
            "  MIN(device_sn) AS sampleDeviceSn,",
            "  COUNT(*) AS totalCount,",
            "  SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pendingCount,",
            "  SUM(CASE WHEN status = 'delivered' THEN 1 ELSE 0 END) AS deliveredCount,",
            "  SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completedCount,",
            "  SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failedCount,",
            "  SUM(CASE WHEN status = 'canceled' THEN 1 ELSE 0 END) AS canceledCount,",
            "  MIN(create_time) AS createTime,",
            "  MAX(update_time) AS lastUpdateTime",
            "FROM device_command",
            "WHERE 1 = 1",
            "<if test='commandType != null and commandType != \"\"'>",
            "  AND command_type = #{commandType}",
            "</if>",
            "GROUP BY command_type, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s'), SHA2(command_text, 256), IFNULL(remark, '')",
            "HAVING 1 = 1",
            "<if test='deviceSn != null and deviceSn != \"\"'>",
            "  AND SUM(CASE WHEN device_sn LIKE CONCAT('%', #{deviceSn}, '%') THEN 1 ELSE 0 END) > 0",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "  AND SUM(CASE WHEN status = #{status} THEN 1 ELSE 0 END) > 0",
            "</if>",
            "ORDER BY createTime DESC",
            "</script>"
    })
    IPage<DeviceCommandGroup> selectGroupPage(Page<?> page,
            @Param("deviceSn") String deviceSn,
            @Param("commandType") String commandType,
            @Param("status") String status);

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            "FROM (",
            "  SELECT 1",
            "  FROM device_command",
            "  WHERE 1 = 1",
            "  <if test='commandType != null and commandType != \"\"'>",
            "    AND command_type = #{commandType}",
            "  </if>",
            "  GROUP BY command_type, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s'), SHA2(command_text, 256), IFNULL(remark, '')",
            "  HAVING 1 = 1",
            "  <if test='deviceSn != null and deviceSn != \"\"'>",
            "    AND SUM(CASE WHEN device_sn LIKE CONCAT('%', #{deviceSn}, '%') THEN 1 ELSE 0 END) &gt; 0",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND SUM(CASE WHEN status = #{status} THEN 1 ELSE 0 END) &gt; 0",
            "  </if>",
            ") grouped_commands",
            "</script>"
    })
    Long selectGroupCount(@Param("deviceSn") String deviceSn,
            @Param("commandType") String commandType,
            @Param("status") String status);

    @Select({
            "<script>",
            "SELECT *",
            "FROM device_command",
            "WHERE " + GROUP_KEY_SQL + " = #{groupKey}",
            "<if test='deviceSn != null and deviceSn != \"\"'>",
            "  AND device_sn LIKE CONCAT('%', #{deviceSn}, '%')",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "  AND status = #{status}",
            "</if>",
            "ORDER BY create_time DESC, id DESC",
            "</script>"
    })
    IPage<DeviceCommand> selectGroupRecords(Page<?> page,
            @Param("groupKey") String groupKey,
            @Param("deviceSn") String deviceSn,
            @Param("status") String status);
}
