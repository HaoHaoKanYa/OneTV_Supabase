# OneTV Supabase 2.0.0

<div align="center">

![版本](https://img.shields.io/badge/版本-2.0.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-brightgreen.svg?logo=jetpack-compose)
![Supabase](https://img.shields.io/badge/Supabase-2.0-green.svg?logo=supabase)
![构建状态](https://img.shields.io/badge/构建-通过-brightgreen)

</div>

## 📱 项目概述

OneTV Supabase是一款现代化的Android电视应用，专为提供流畅的直播观看体验而设计。本项目完成了从Cloudflare到Supabase的全面迁移，采用最新的Android开发技术和多模块架构，实现了更快、更稳定、功能更丰富的用户体验。本软件仅供技术研究与学习交流使用，严禁用于任何商业场景或非法用途。

## 构建状态



| 工作流 | 状态 |

|-------|------|

| Android CI | ![Android CI](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/android.yml/badge.svg) |

| Release | ![Release](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/release.yaml/badge.svg) |

| Supabase Deploy | ![Supabase Deploy](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/supabase-deploy.yml/badge.svg) |

| Supabase Config | ![Supabase Config](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/check-supabase-config.yml/badge.svg) |





项目已成功完成从Cloudflare到Supabase的全面迁移，主要改进包括：

### 用户认证系统

- 使用Supabase Auth进行用户注册、登录、密码重置
- 实现Auth UI组件完成认证流程
- 优化登录流程，显著提升用户体验

### 数据存储与管理

- 创建完整的数据表结构：用户资料、登录日志、频道收藏、用户设置等
- 实现数据迁移机制，确保用户数据安全迁移
- 建立高效的数据同步机制

### 性能优化

- 登录流程优化：实现三阶段智能分层加载策略
  - **阶段1 (0-1秒)**：关键操作，必须同步执行
  - **阶段2 (1-3秒)**：功能数据，后台执行
  - **阶段3 (3秒+)**：重型数据，延迟执行
- 观看历史同步优化：解决重复记录问题，提高同步效率
- 缓存机制改进：智能缓存策略，减少网络请求，提高响应速度

## 📁 项目结构

项目采用多模块架构设计，提高代码复用性和可维护性：

```
OneTV_Supabase/
├── core/                # 核心模块
│   ├── data/            # 数据处理
│   │   └── repositories/  # 数据仓库
│   ├── designsystem/    # UI设计系统
│   │   └── theme/         # 主题定义
│   └── util/            # 通用工具
│       └── utils/         # 工具类
├── mobile/              # 手机应用模块
├── tv/                  # 电视应用模块
│   ├── supabase/        # Supabase集成
│   └── ui/              # UI组件
│       ├── material/      # 材料设计组件
│       ├── screens/       # 应用屏幕
│       └── theme/         # 主题实现
└── supabase/            # Supabase后端配置
    └── sql/             # 数据库迁移脚本
```

## 🔍 主要优化成果

### 1. 登录流程优化

- **用户感知登录时间**: 从 10-12秒 → **1-2秒** ✅
- **主界面可用时间**: 立即可用 ✅
- **后台数据加载**: 不阻塞用户操作 ✅
- **整体用户体验**: 显著提升 ✅

### 2. 观看历史同步优化

- **重复记录问题**: 解决服务器数据库中出现大量重复记录的问题
- **同步效率**: 优化客户端同步逻辑，减少无效请求
- **数据一致性**: 确保每个观看记录只存储一次
- **唯一性约束**: 数据库层面防止重复插入

### 3. 多线路功能优化

- **界面交互**: 改进多线路切换界面，提供更直观的用户体验
- **切换速度**: 优化线路切换逻辑，减少切换时间
- **状态管理**: 改进线路状态管理，提高稳定性

### 4. 性能与稳定性提升

- **启动速度**: 提升50%
- **内存占用**: 减少30%
- **崩溃率**: 显著降低
- **网络效率**: 优化网络请求，减少数据传输

## 🚀 开发路线图

### 当前版本 (2.0.0)

- ✅ 完成Supabase迁移
- ✅ 全新UI界面设计
- ✅ 多模块架构实现
- ✅ 登录流程优化
- ✅ 观看历史同步优化

### 计划版本 (2.1.0)

- [ ] 扩展多平台支持
- [ ] 增强用户个性化体验
- [ ] 改进频道管理功能
- [ ] 优化网络性能
- [ ] 高级频道过滤和分组

### 未来展望 (3.0.0)

- [ ] 桌面平台支持
- [ ] 高级VIP功能
- [ ] 直播功能
- [ ] AI内容推荐与搜索
- [ ] 云同步和多设备功能增强

详细路线图请参考[PROJECT_ROADMAP.md](MD/PROJECT_ROADMAP.md)

## 📋 环境要求

- Android Studio 2023.3.1 或更高版本
- JDK 17+
- Gradle 8.6+
- Android SDK 34 (最低支持 API 26)
- Kotlin 2.1.10

## 📚 相关文档

- [更新日志](MD/CHANGELOG.md)
- [迁移指南](MD/MIGRATION_GUIDE.md)
- [观看历史优化总结](MD/观看历史上传优化总结.md)
- [登录优化总结](MD/LoginOptimizationSummary.md)
- [用户协议与免责声明](tv/src/main/assets/User_Agreement_And_Disclaimer.md)

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出功能建议。请参阅[CONTRIBUTING.md](CONTRIBUTING.md)了解详细的贡献流程。

## 📢 免责声明

### 软件性质与基本声明

- **使用限制**：本软件仅供技术研究与学习交流使用，严禁用于任何商业场景或非法用途。用户不得对本软件进行二次贩卖、捆绑销售或用于盈利性服务。
- **内容免责**：本软件自身不制作、不存储、不传播任何音视频内容。所有直播流均来源于用户自定义添加或第三方网络公开资源。开发者对内容的合法性、准确性及稳定性不做任何担保，亦不承担相关责任。
- **开发性质**：本软件为个人开发者开源学习项目，无商业团队运营、无公司主体。软件内涉及的代码、UI设计及文档资源均基于开发者社区公开贡献构建。

### 用户责任与承诺

- **合规使用**：用户应遵守所在地区法律法规，合理使用网络资源。严禁利用本软件从事违法活动或接入非法内容源。应用仅供个人学习和测试使用，请在24小时内删除。
- **风险承担**：用户需自行确保所播放内容符合所在地法律法规。因用户行为导致的版权纠纷、数据滥用等后果需自行承担，与本软件及开发者无关。

### 技术免责

- 不保证与所有设备/系统版本兼容
- 本服务可能因不可预知原因导致功能暂时不可用，开发者不承担连带责任
- 升级至2.0.0版本与旧版本不完全兼容，升级后可能需要重新登录

更多详细内容请参阅[用户协议与免责声明](tv/src/main/assets/User_Agreement_And_Disclaimer.md)

## 📄 许可证

本项目采用[LICENSE](LICENSE)许可证。
