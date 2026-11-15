-- ============================================================
-- AI 导购模块表结构
-- 在 newbee_mall_db 中执行本脚本
-- ============================================================

CREATE TABLE IF NOT EXISTS `tb_newbee_mall_ai_chat_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志id',
  `user_id` bigint DEFAULT NULL COMMENT '用户id,匿名会话为空',
  `session_id` varchar(64) NOT NULL COMMENT '会话id',
  `question` varchar(1000) NOT NULL COMMENT '用户问题',
  `answer` text COMMENT 'AI回答(截断)',
  `latency_ms` int NOT NULL DEFAULT 0 COMMENT '耗时(毫秒)',
  `success` tinyint NOT NULL DEFAULT 1 COMMENT '是否成功 1成功 0失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_ai_chat_log_session` (`session_id`),
  KEY `idx_ai_chat_log_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI导购对话审计日志';
