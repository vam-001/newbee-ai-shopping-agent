# 上游项目与致谢

## 上游项目

- 项目：[newbee-mall](https://github.com/newbee-ltd/newbee-mall)
- 分支：`spring-boot-3.x`
- 上游 commit：`eb71557`
- 作者：十三 / newbee-ltd
- 许可证：GPL-3.0

本项目是上述项目的衍生作品（derivative work），在保留上游电商功能的基础上新增了 AI 导购 Agent 模块。

## 许可证

本项目与上游一致，采用 **GNU General Public License v3.0**。完整许可证见 [LICENSE](LICENSE)。

重新分发本项目或其衍生作品时，请：

1. 保留本文件与 `LICENSE`；
2. 保留源文件中上游作者的版权头；
3. 以 GPL-3.0 开源衍生作品；
4. 注明对上游项目的修改。

## 本项目的增量

- Spring Boot 3.1.0 → 3.5.7、MyBatis Starter 3.0.2 → 3.0.5 的升级与适配；
- `ltd.newbee.mall.ai` 包：Agent、工具、RAG、Redis 会话记忆、限流、输入安全、审计、控制器；
- `tb_newbee_mall_ai_chat_log` 表与 Mapper；
- `/ai/chat` 页面、SSE 前端与导航入口；
- 10 个单元测试；
- 数据库/Redis/AI 配置外部化。

上游的商品、分类、购物车、订单、后台管理等代码保持原样（除配置与升级所需的必要修改）。

## 第三方组件

| 组件 | 许可证 |
|---|---|
| newbee-mall | GPL-3.0 |
| Spring Boot / Spring Framework | Apache-2.0 |
| LangChain4j | Apache-2.0 |
| BGE-small-zh-v1.5（LangChain4j 打包） | MIT（模型卡以官方为准） |
| ONNX Runtime | MIT |
| MySQL Connector/J | GPL-2.0 with FOSS Exception |
| Hutool | Mulan PSL v2 |
