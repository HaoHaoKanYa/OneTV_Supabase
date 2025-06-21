# OneTV Supabase 2.0.0

<div align="center">

![版本](https://img.shields.io/badge/版本-2.0.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-brightgreen.svg?logo=jetpack-compose)
![Supabase](https://img.shields.io/badge/Supabase-2.0-green.svg?logo=supabase)

</div>

## 构建状态



| 工作流 | 状态 |

|-------|------|

| Android CI | ![Android CI](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/android.yml/badge.svg) |

| Release | ![Release](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/release.yaml/badge.svg) |

| Supabase Deploy | ![Supabase Deploy](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/supabase-deploy.yml/badge.svg) |

| Supabase Config | ![Supabase Config](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/check-supabase-config.yml/badge.svg) |





- **全平台架构重构**：采用 Kotlin Multiplatform 技术，实现代码共享，支持 Android TV 和移动设备
- **Supabase 集成升级**：完全利用 Supabase 2.0 API，提高数据安全性和可靠性
- **全新 UI 设计**：完全重新设计的透明 UI 界面，视觉效果更现代化，操作更直观
- **性能优化**：启动速度提升 50%，加载速度提升 50%，内存占用减少 30%
- **多端同步**：用户配置和收藏在所有设备间实时同步，无缝切换体验
- **高级搜索**：支持模糊搜索和语音搜索功能，快速找到想要的内容
- **个性化推荐**：基于用户观看历史的智能推荐系统，发现更多喜爱内容
- **离线缓存**：支持频道信息离线缓存，减少网络依赖，提升弱网环境下的体验
- **深色模式**：自动适应系统深色模式设置，保护用户视力

## 🔧 技术架构

### 前端技术栈

- **UI 框架**：Jetpack Compose，实现声明式 UI 和动画效果
- **架构模式**：MVVM + Clean Architecture，提高代码可维护性
- **状态管理**：Kotlin Flow + StateFlow，响应式编程
- **依赖注入**：Koin，轻量级 DI 框架
- **网络请求**：Ktor Client，支持协程和多平台
- **图片加载**：Coil，高效的图片加载和缓存
- **视频播放**：ExoPlayer，支持多种格式和高级播放功能
- **本地存储**：DataStore + Room，高效的数据持久化

### 后端技术栈 (Supabase)

- **认证系统**：Supabase Auth，支持多种登录方式和安全认证
- **数据存储**：PostgreSQL 数据库，强大的关系型数据存储
- **实时更新**：Realtime API，提供数据实时同步
- **Edge Functions**：无服务器函数，处理复杂业务逻辑
- **存储系统**：Storage API，管理用户上传的内容
- **安全策略**：Row Level Security，精细的权限控制

## 📋 环境要求

- Android Studio Iguana (2023.2.1) 或更高版本
- JDK 17+
- Gradle 8.6+
- Android SDK 34 (最低支持 API 21)
- Kotlin 2.1.10
- Node.js 18+ (用于 Supabase 本地开发)

## 📁 项目结构

```
OneTV_Supabase/
├── app/                  # 移动应用模块
├── tv/                   # TV 应用模块
├── shared/               # 共享代码模块
│   ├── data/             # 数据层实现
│   ├── domain/           # 领域层（业务逻辑）
│   └── presentation/     # 表现层共享组件
├── supabase/             # Supabase 配置和函数
│   ├── functions/        # Edge Functions
│   └── migrations/       # 数据库迁移脚本
├── buildSrc/            # Gradle 构建逻辑
└── .github/workflows/   # CI/CD 工作流配置
```

## 🚀 快速开始

### 环境配置

1. **克隆仓库**
   ```bash
   git clone https://github.com/HaoHaoKanYa/OneTV_Supabase.git
   cd OneTV_Supabase
   ```

2. **配置 Supabase 凭据**
   - 复制 `supabase_config.properties.example` 为 `supabase_config.properties`
   - 填入你的 Supabase URL 和 API Key
   ```properties
   BOOTSTRAP_URL=https://your-project-id.supabase.co
   BOOTSTRAP_KEY=your-anon-key
   ```

3. **配置签名密钥**
   - 创建 `key.properties` 文件并配置签名信息
   ```properties
   storeFile=your_keystore.jks
   storePassword=your_store_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```

### 构建和运行

- **构建 TV 版本**
  ```bash
  ./gradlew :tv:assembleDebug
  ```

- **构建移动版本**
  ```bash
  ./gradlew :app:assembleDebug
  ```

- **运行单元测试**
  ```bash
  ./gradlew test
  ```

## 📱 版本发布流程

项目使用 GitHub Actions 实现自动化构建和发布：

1. 创建新的版本标签（如 `v2.0.1`）
2. 推送标签触发 `Release` 工作流
3. 工作流自动构建 APK 并创建 GitHub Release
4. `Update Release JSON` 工作流自动更新 `tv-stable.json`
5. 应用内更新系统会检测到新版本并提示用户升级

详细流程请参考 [RELEASE_GUIDE.md](RELEASE_GUIDE.md)

## 📄 相关文档

- [更新日志](CHANGELOG.md) - 详细的版本更新记录
- [发布指南](RELEASE_GUIDE.md) - 版本发布流程说明
- [Supabase 配置指南](supabase/README.md) - Supabase 后端配置说明
- [贡献指南](CONTRIBUTING.md) - 参与项目开发的指南

## 📞 联系与支持

- **问题反馈**：通过 [GitHub Issues](https://github.com/HaoHaoKanYa/OneTV_Supabase/issues) 提交
- **公众号**：【壹来了】获取最新动态和支持
- **讨论区**：使用 [GitHub Discussions](https://github.com/HaoHaoKanYa/OneTV_Supabase/discussions) 参与讨论

## 📝 免责声明

OneTV 仅为技术演示应用，不提供任何直播源。用户需自行添加自定义直播源，并对使用内容负责。应用仅供个人学习和测试使用，请在 24 小时内删除。本项目不对任何因使用本软件而导致的问题负责。
