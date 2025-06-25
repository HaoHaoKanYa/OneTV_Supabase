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
