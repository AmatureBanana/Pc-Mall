# 商品管理契约

任务05沿用现有商品、分类参数、Session、CSRF和ADMIN权限。入口为 `/#admin/products`。八类参数表单从同一服务端字段定义读取；`AdminField.vue` 为后续后台表单提供带标签、长度和禁用状态的基础字段。

## 管理接口

全部管理接口位于 `/api/admin/products`，匿名401，USER为403。POST必须带当前Session的CSRF令牌。

| 方法 / 路径 | 行为 |
| --- | --- |
| GET `/api/admin/products` | `keyword`匹配名称、SKU、品牌、型号；可组合`category`、`status`、`page`、`size`，返回`data: {items,total,page,size}`；页码从1开始，每页1～100 |
| GET `/api/admin/products/{id}` | 包含下架商品的完整详情、当前价格、库存、参数及`revision` |
| GET `/api/admin/products/schema?category=CPU` | 分类字段的类型、单位、上架必需标识及合法词汇 |
| POST `/api/admin/products` | 新增默认OFF_SALE的草稿；必须提供唯一SKU及八类之一，其他资料可逐步完善 |
| POST `/api/admin/products/{id}` | 完整替换可编辑资料与参数；必须提交最近读取的`revision`；SKU、类别、价格、库存、状态和ID不接受修改 |
| POST `/api/admin/products/{id}/status` | 只接受`revision`及ON_SALE/OFF_SALE；上架按当前保存资料校验；相同状态不增加版本或重复审计 |
| GET `/api/admin/products/{id}/audit` | 核验该商品最近100条审计，含操作者ID/用户名、时间、对象、操作及前后快照；不提供P1专用审计筛选页面 |

新增SKU为1～80位字母、数字、点、下划线或短横线，首位字母或数字，沿用数据库不区分大小写的唯一约束。商品ID由MySQL生成；初始库存为0。可选初始售价为大于零、最多两位小数的CNY字符串，不传则以0保存未定价草稿；上架要求售价大于零。后续调价、库存调整及原因记录由任务07的受控服务交付，通用商品编辑不能改写这些字段。

基础信息及参数类型/长度/词汇错误为400；重复SKU为409/`DUPLICATE_SKU`，版本过期为409/`PRODUCT_CONFLICT`，缺参上架为400/`PRODUCT_NOT_READY`并列出原因，不存在为404。数据库及审计写入失败为503，原业务保持不变。已上架商品编辑后仍须满足上架要求，否则拒绝整个编辑。

## 参数与单位

参数沿用`{code,label,value,unit,sourceUrl,note}`结构，标签由服务端规范化。缺失/`null`表示未知，不能替代false或已核验空集合；集合不允许重复。`displayOutputs`、`gpuInterfaces`可用空集合明确无该能力，检测阶段应据此判定路径，不能把未知当作可用。所有非null参数上架时须有HTTP(S)来源地址。

服务端字段定义覆盖现有V4字段及全部F03-C01～C10逻辑需要。接受的插槽、DDR代际、板型、视频输出、安装接口等词汇以schema为准；新词汇需在统一模型中补充，不能通过展示摘要绕过校验。功率为W、尺寸为mm、内存容量为GB且实际条数单列；提供单条容量时须满足总容量=条数×单条容量。数值必须为正数，条数/插槽数必须为整数。

SSD同时保留`originalCapacity`数值与GB/TB/GiB/TiB单位，以及十进制GB的`capacity`，并验证精确换算：1TB=1000GB、1GiB=1.073741824GB、1TiB=1099.511627776GB。

主板新增`ssdSlots`，值为有效`{formFactor,interface,protocol}`组合的集合。独立的旧规格/接口/协议集合保留用于展示，后续规则应使用组合，避免把不同插槽的能力做笛卡尔积；组合数不等于物理插槽数，M.2总数仍由`m2Slots`表达。V10仅将V4已核验的两款主板PCIe/NVMe组合显式化，保留原核验日期，不填充未知CPU峰值。

## 事务与复用

V9增加商品`revision`和通用`admin_audit`表；写服务先锁商品行，再核对版本。基础编辑和上下架递增版本，同一旧版本的并发编辑最多一个成功。描述/参数版本随编辑变化，公开商品查询继续从同一数据库读取。

`AdministrationAudit.record`要求调用方已经开启事务，以MANDATORY传播加入业务事务。新增、编辑、上下架与审计一起提交或回滚；快照只包含商品资料，不存密码或Session。后续用户、价格、库存和订单管理复用此服务，在各自领域事务中传入对象类型与必要前后值。无商品物理删除接口，也不更新其他业务历史数据。

UI及业务实现为本项目代码，复用现有锁定Element Plus表格、分页和状态组件；没有复制Geeker-Admin代码或导入pc-builder-parts数据，无新增第三方依赖。
