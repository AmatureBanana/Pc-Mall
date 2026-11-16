# 7800X3D核显配置未知功耗修复

日期：2026-11-16。

## 原因与来源

截图配置为7800X3D、MSI B650M MORTAR WIFI、单根Kingston DDR5、990 PRO、RM750e、CC560、AK400及CPU核显。真实MySQL的公开HTTP检测返回C08 UNKNOWN：CPU `peakPowerEstimate=null`，PSU `ratedPower=750`，核显没有误要求独显功耗。最小功率判断只依赖CPU和电源；这不是插槽冲突或电源功率不足。

V4原先只有AMD产品页的120W TDP，正确保留未知，不能拿它代替峰值。此次依据[SkatterBencher #60的型号级原始测试记录](https://skatterbencher.com/2023/04/05/skatterbencher-60-amd-ryzen-7-7800x3d-overclocked-to-5400-mhz/)补录默认PPT 162W，作为保守功耗估算。来源作者Pieter，文章2023-04-05发布、页面显示2026-05-20更新，2026-11-16核对。引用的是作者实际测试该型号并介绍默认限制的一手记录，不是转载汇总或社区问答；只采纳事实数值与自行表述备注，没有复制文章、代码或图片。

它不是AMD产品页发布的标称峰值，不是实测持续功耗，也不是通过TDP乘系数推算；不涵盖手动超频或提高功率限制。[AMD产品页](https://www.amd.com/en/products/processors/desktops/ryzen/7000-series/amd-ryzen-7-7800x3d.html)的120W TDP保持独立。PPT概念可参照[AMD Ryzen Master参考指南](https://www.amd.com/content/dam/amd/en/documents/products/software-tools/ryzen-master-quick-reference-guide.pdf)。

## 实现

V26仅为指定SKU的现有空功耗字段补值、字段来源和方法说明，更新参数版本/核验日期与商品修订；已有非空维护值不覆盖，不改型号、价格、库存、评分或其他参数。C08算法保持不变，仍拒绝真正缺少参数/来源的配置。

此核显方案的冻结模型结果为 `ceil(1.30 × (162 + 0 + 80)) = 315W`，750W额定电源可通过。完整配置和最小CPU+PSU场景通过公开HTTP回归验证；浏览器回归验证截图配置显示核心通过并能打开加购确认。

## 验证

`CompatibilityCheckTest#ryzen7800x3dIntegratedBuildHasVerifiedPowerAndCanPassCoreChecks` 在修复前真实红灯：预期PASS，实际UNKNOWN；日志 `.local/amd-igpu-power-red.log`。首条本机8080探测因服务未运行未能复现，不把它算作业务证据。

兼容规则专项初轮55/55通过，日志 `.local/amd-igpu-power-green.log`。完整回归初轮因V26执行后误改注释触发Flyway校验失败（`.local/amd-power-tests.log`）；已恢复原始迁移文本，没有关闭校验、修改历史校验和或执行repair。

最终完整后端186/186通过（0失败、0错误、0跳过），TypeScript、Vite及同源JAR通过，Edge核显和兼容诊断两个文件15/15通过。新浏览器回归使用截图同型号组合，确认核心通过、未知提示消失并能打开加购确认。脚本 `.local/amd-power-tests.ps1` 复用正式隔离MySQL/临时服务与管理员清理流程。最终日志 `.local/amd-power-tests-final.log`；测试重写的旧PNG已恢复并逐文件核对哈希一致，原有AGENTS与IDE配置变更未动。

已启动本地8080演示应用并自动应用V26，通过真实Session/CSRF的只读兼容接口复核演示库：`status=CORE_PASSED`、`corePassed=true`、C08 PASS，明细为CPU估算162W、电源额定750W、冻结模型需求315W。未调用加购、下单或真实支付。应用日志 `.local/amd-power-demo.log`，用户刷新后重新检测即可使用新参数。
