# OpenCode 自动编程方案备份

本目录保存插件化改造前的完整可恢复基线。备份动作只复制和记录资源，
没有修改 `.opencode/`、`automation/`、`scripts/automation/` 等现有方案文件。

## 备份基线

| 项目 | 值 |
| --- | --- |
| 备份时间 | 2026-08-19 20:14:29 +0800（Asia/Shanghai） |
| Git 分支 | `feature-opencode-scheduler` |
| Git HEAD | `829693652e3737ad94c7cc75214b09fb2b58715b` |
| HEAD 说明 | `提交新的自动编码方案` |
| 工作树 | 备份前干净；只有当前主工作目录，无任务 worktree |
| OpenCode | `1.15.13` |
| 本地插件 SDK | `@opencode-ai/plugin@1.15.13` |
| 方案版本 | OpenCode coding orchestration V3 / schemaVersion 3 |
| 默认工作区策略 | `inPlaceExclusive` |
| 原分支漂移策略 | `block` |

## 目录内容

```text
方案备份/
├── README.md                       # 本入口
├── 当前方案总结.md                  # 行为、角色、状态机与安全边界
├── 资源清单.md                      # 所有备份资源及用途
├── 恢复说明.md                      # 安全还原步骤与注意事项
├── 验证记录.md                      # 备份前测试和快照一致性证据
├── backup-metadata.json            # 机器可读基线信息
├── verify-backup.sh                # 只读完整性检查
├── snapshot/repository/            # 仓库自有方案资源，70 个文件
├── external-dependencies/          # 精确第三方包副本，183 个文件，本机保留
└── runtime-audit/                   # Git common dir 运行审计，79 个文件，本机保留
```

`external-dependencies/` 和 `runtime-audit/` 已由本目录的 `.gitignore`
标记为本机备份：前者可由固定版本重新安装，后者可能包含绝对路径、日志和
任务 diff，不应无意提交或对外发布。文件仍真实存在于本目录中。

## 快速检查

从仓库根目录执行：

```bash
./方案备份/verify-backup.sh
```

预期输出三个 `OK` 和 `backup verification passed`。该脚本只读取备份，
不会还原或改写现有方案。

## 还原原则

不要直接删除当前文件，也不要执行宽泛的 `git reset` 或 `git clean`。
先阅读 [恢复说明.md](./恢复说明.md)，确认插件化改造实际修改了哪些路径，
再用 `snapshot/repository/` 覆盖这些已知路径。插件化新增文件必须依据改造
清单逐个处理，备份不会猜测并删除未知文件。

当前方案的完整行为说明见 [当前方案总结.md](./当前方案总结.md)，逐项资源
映射见 [资源清单.md](./资源清单.md)。
