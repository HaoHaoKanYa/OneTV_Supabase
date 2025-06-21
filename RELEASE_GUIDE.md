# OneTV Supabase 发布流程指南

本文档详细说明了 OneTV Supabase 项目的版本发布流程，包括更新日志管理、创建发布标签、自动构建和发布 APK，以及应用内更新机制。

## 目录

1. [文件说明](#文件说明)
2. [发布新版本的完整流程](#发布新版本的完整流程)
3. [更新日志管理](#更新日志管理)
4. [GitHub Actions 工作流说明](#github-actions-工作流说明)
5. [应用内更新机制](#应用内更新机制)
6. [常见问题与解决方案](#常见问题与解决方案)

## 文件说明

项目中与发布流程相关的关键文件：

- **CHANGELOG.md**：记录所有版本的更新内容，作为项目历史文档
- **tv-stable.json**：应用内更新检查使用的文件，包含最新版本信息
- **.github/workflows/release.yaml**：负责构建 APK 并创建 GitHub Release
- **.github/workflows/update-release-json.yml**：负责更新 tv-stable.json 文件

## 发布新版本的完整流程

### 1. 更新 CHANGELOG.md

在 CHANGELOG.md 文件顶部添加新版本的更新日志，格式如下：

```markdown
## [版本号] - 发布日期

### 重大更新

- **更新点1**：详细说明
- **更新点2**：详细说明

### 新增功能

- **功能1**：详细说明
- **功能2**：详细说明

### 技术改进

- **改进1**：详细说明
- **改进2**：详细说明

### 修复问题

- 修复问题1
- 修复问题2

### 其他改进

- 其他改进1
- 其他改进2

> 注意事项（如果有）
```

### 2. 提交 CHANGELOG.md 更改

```bash
git add CHANGELOG.md
git commit -m "准备发布 OneTV Supabase v版本号"
git push origin main
```

### 3. 创建并推送版本标签

这一步会触发 release.yaml 工作流：

```bash
git tag v版本号
git push origin v版本号
```

### 4. 监控 GitHub Actions 工作流

1. 在 GitHub 仓库的 Actions 选项卡中监控工作流执行情况
2. release.yaml 工作流会构建 APK 并创建 GitHub Release
3. update-release-json.yml 工作流会更新 tv-stable.json 文件

### 5. 检查 Release 内容

当工作流完成后：
1. 检查 GitHub Release 页面，确保 APK 已上传
2. 确认 Release 描述包含正确的更新日志内容
3. 如需修改 Release 描述，可在 GitHub 界面上手动编辑

### 6. 验证 tv-stable.json 更新

确认 tv-stable.json 文件已更新为新版本信息，包含：
- 正确的版本号
- APK 下载链接
- 完整的更新日志内容

## 更新日志管理

### CHANGELOG.md 文件

- CHANGELOG.md 是项目的历史文档，应保留所有版本的更新记录
- 新版本信息应添加在文件顶部
- 格式应保持一致，便于阅读和维护

### Release 描述与应用内更新内容

应用内显示的更新内容来自 tv-stable.json 文件的 "description" 字段，而这个字段是由 GitHub Release 的描述自动更新的。

控制应用内显示的更新内容有两种方式：
1. 在创建 Release 时，确保 Release 描述中只包含你想在应用内显示的内容
2. 在 Release 创建后，手动编辑 Release 描述

## GitHub Actions 工作流说明

### release.yaml 工作流

触发条件：推送带有 `v*` 标签的提交

主要步骤：
1. 检出代码
2. 设置 Java 环境和 Gradle
3. 配置 Supabase 和 Android Keystore
4. 构建 Release 版本的 APK
5. 生成更新日志
6. 创建 GitHub Release 并上传 APK 和 AAB 文件

### update-release-json.yml 工作流

触发条件：新的 Release 被发布

主要步骤：
1. 获取 Release 的标签名、URL、发布日期和版本号
2. 获取 APK 的下载链接
3. 更新 tv-stable.json 文件
4. 提交并推送更改到主分支

## 应用内更新机制

应用程序通过检查 tv-stable.json 文件来确定是否有新版本可用：

1. 应用启动或用户手动检查更新时，会请求 tv-stable.json 文件
2. 比较文件中的版本号与当前应用版本
3. 如果有新版本，显示更新提示，包含 "description" 字段的内容
4. 用户确认后，下载 "downloadUrl" 字段指定的 APK

## 常见问题与解决方案

### 工作流执行失败

#### Actions 版本问题

错误示例：
```
Unable to resolve action `actions/checkout@v4`, repository or version not found
```

解决方案：
修改 .github/workflows 目录下的工作流文件，将 v4 改为 v3 或检查最新的可用版本。

#### Secrets 配置问题

确保所有必要的 secrets 已正确配置：
- BOOTSTRAP_URL
- BOOTSTRAP_KEY
- SUPABASE_ACCESS_TOKEN
- KEYSTORE_BASE64
- KEYSTORE_PASSWORD
- KEY_ALIAS
- KEY_PASSWORD

### 手动发布流程

如果自动工作流持续失败，可以采用手动流程：

1. 手动构建 APK：
   ```bash
   ./gradlew assembleRelease
   ```

2. 在 GitHub 界面上手动创建 Release：
   - 上传构建的 APK
   - 填写更新日志内容

3. 手动更新 tv-stable.json 文件：
   ```json
   {
     "version": "版本号",
     "downloadUrl": "APK下载链接",
     "description": "更新内容"
   }
   ```

### tv-stable.json 未更新

如果 Release 创建后 tv-stable.json 未更新：

1. 检查 update-release-json.yml 工作流的执行日志
2. 确认工作流有足够的权限提交和推送更改
3. 必要时手动更新 tv-stable.json 文件

---

本指南将随项目发展持续更新。如有问题或建议，请提交 Issue 或联系项目维护者。 