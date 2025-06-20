# OneTV  2.0.0

![版本](https://img.shields.io/badge/版本-2.0.0-blue)
![构建状态](https://img.shields.io/badge/构建-通过-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg?logo=kotlin)

OneTV是一款基于Android TV的全新流媒体应用，使用Su云作为后端服务。本项目从1.41版本直接跳更至2.0.0，带来全新的用户体验和更稳定的性能。

## 构建状态



| 工作流 | 状态 |

|-------|------|

| Android CI | ![Android CI](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/android.yml/badge.svg) |

| Release | ![Release](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/release.yaml/badge.svg) |

| Supabase Deploy | ![Supabase Deploy](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/supabase-deploy.yml/badge.svg) |

| Supabase Config | ![Supabase Config](https://github.com/HaoHaoKanYa/OneTV_Supabase/actions/workflows/check-supabase-config.yml/badge.svg) |





- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **后端**: Su云 (身份验证、数据库、存储、函数)
- **依赖注入**: Koin
- **网络**: Ktor
- **异步处理**: Kotlin Coroutines
- **图片加载**: Coil

## 📋 环境要求

- Android Studio 2023.3.1 或更高版本
- JDK 17+
- Gradle 8.6+
- Android SDK 34 (最低支持 API 26)
- Kotlin 2.1.10

## 🚀 快速开始

### 环境变量配置

项目需要配置以下环境变量:

```properties
见应用内
```

### 本地开发

1. 见应用内
2. 见应用内
3. 使用Android Studio打开项目并同步Gradle
4. 运行应用

## 📱 版本升级说明

从1.41版本升级到2.0.0版本的主要变更:

- **架构升级**: 采用现代MVVM架构，提高代码可维护性
- **API更新**: 全新的API设计，与Su云无缝集成
- **数据迁移**: 自动数据迁移机制，确保用户数据安全迁移
- **性能改进**: 全面优化应用性能，减少内存占用，提高响应速度
- **多媒体引擎**: 升级底层播放引擎，支持更多视频格式和更高分辨率

## 📁 项目结构

```
见应用内
```

## 📄 相关文档

- [更新日志](CHANGELOG.md)
- 更新说明
- [应用配置说明](README_app_configs.md)
- [部署指南](DEPLOYMENT.md)

## 📞 联系方式

- 公众号: 【壹来了】
- 问题反馈: 请通过GitHub Issues提交

## 📝 免责声明

OneTV仅为空壳软件，不提供任何直播源。用户需自行添加自定义直播源，并对使用内容负责。应用仅供个人学习和测试使用，请在24小时内删除。

## 系统要求

- Python 3.6或更高版本
- 无需额外依赖库
