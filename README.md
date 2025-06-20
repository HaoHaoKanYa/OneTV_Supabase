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

## 🌟 主要特性

- **全新UI界面**: 完全重新设计的透明UI，视觉效果更加现代化
- **Su集成**: 完全迁移至Su云，提供更可靠的后端服务
- **多平台支持**: 使用Kotlin Multiplatform技术，支持Android TV和手机设备
- **个性化体验**: 用户配置文件和收藏频道同步功能
- **安全认证**: 完善的用户认证系统，支持注册、登录和密码重置
- **性能优化**: 全新的缓存机制，减少加载时间，提升用户体验

## 🔧 技术栈

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

# 随机激活码生成器

这是一个简单的Python脚本，用于生成指定数量和长度的随机激活码，并支持设置激活码有效期。

## 功能特点

- 生成随机32位（可自定义长度）激活码
- 支持批量生成多个激活码
- 可设置激活码有效期（如10天、30天、365天等）
- 可设置激活码具体过期日期（如2025-12-31）
- 自动将生成的激活码保存到文本文件
- 支持简单格式（仅激活码）或CSV格式（激活码,天数,过期日期）输出
- 可以自定义输出文件名
- 命令行界面，便于使用

## 使用方法

基本用法：

```bash
python py/generate_activation_codes.py
```

这将生成10个默认长度为32位的激活码，有效期30天，并保存到带时间戳的文本文件中。

### 可选参数

- `-n, --number`：指定要生成的激活码数量（默认：10）
- `-l, --length`：指定激活码长度（默认：32）
- `-d, --days`：指定激活码有效期天数（默认：30）
- `-e, --expiry-date`：指定激活码具体过期日期，格式为YYYY-MM-DD（例如：2025-12-31）
- `-o, --output`：指定输出文件名（默认：activation_codes_YYYYMMDD_HHMMSS.txt）
- `--format`：指定输出格式，可选 simple（仅激活码）或 csv（激活码,天数,过期日期）（默认：csv）

**注意**：`-d`和`-e`参数是互斥的，不能同时使用。

### 示例

生成100个激活码：
```bash
python py/generate_activation_codes.py -n 100
```

生成16位长的激活码：
```bash
python py/generate_activation_codes.py -l 16
```

生成365天有效期的激活码：
```bash
python py/generate_activation_codes.py -d 365
```

生成过期日期为2025-12-31的激活码：
```bash
python py/generate_activation_codes.py -e 2025-12-31
```

指定输出文件名：
```bash
python py/generate_activation_codes.py -o my_activation_codes.txt
```

生成仅包含激活码的简单格式输出：
```bash
python py/generate_activation_codes.py --format simple
```

组合使用多个参数：
```bash
python py/generate_activation_codes.py -n 50 -l 24 -d 180 -o half_year_codes.txt
```

生成圣诞节到期的激活码：
```bash
python py/generate_activation_codes.py -n 50 -l 32 -e 2025-12-25 -o christmas_codes.txt
```

## 输出格式

默认情况下，生成的文件采用CSV格式，每行的格式为：
```
激活码,有效天数,过期日期
```

例如：
```
a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6,30,2023-05-01
```

如果指定了`--format simple`参数，则每行只包含激活码：
```
a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
```

## 系统要求

- Python 3.6或更高版本
- 无需额外依赖库
