# PLAN:手持 Slash 元件(AE2 便携元件,8 格拔刀剑)

> 状态:分支 A 已定(继承 AE2 便携元件,需充电)。API 全部 javap 实证,见下表。
> 产物:`applied_slash:portable_slash_cell`(手持物品)+ 加伤池改造(统一 10%,元件内优先,合计上限 8 把)。

## 1. 已钉死的 API(实证,禁止凭记忆改)

| 用途 | 签名 / 常量 | 来源 |
|---|---|---|
| 基类 | `appeng.items.tools.powered.AbstractPortableCell(MenuType<?>, Item.Properties, int defaultColor)`;`extends PoweredContainerItem extends AEBasePoweredItem`,实现 `IMenuItem` + `ICellWorkbenchItem` | javap |
| 菜单类型 | `appeng.menu.me.common.MEStorageMenu.PORTABLE_ITEM_CELL_TYPE` | javap(1.21.1-19.2.17:`AEItems` 注册便携物品元件时传的就是它) |
| 供电 | 构造时 `AEConfig.instance().getPortableCellBattery()`;`EnergyCellBlockItem.injectAEPower(...)` | javap |
| 存储挂点 | `PortableCellMenuHost$CellStorageSupplier.get()` → `StorageCells.getCellInventory(ItemStack, ISaveProvider)` ⇒ **注册自己的 `ICellHandler`** | javap |
| 存储接口 | `StorageCell extends MEStorage`:`getStatus()` / `getIdleDrain()` / `canFitInsideCell()` / `persist()`;`ICellHandler`:`isCell(ItemStack)` / `getCellInventory(ItemStack, ISaveProvider)` | javap |
| 菜单宿主 | `PortableCellMenuHost implements IPortableTerminal extends ITerminalHost, IEnergySource` | javap |

## 2. 数值与规则

- **每把刀 +10%**(`CELL_RATIO = HOTBAR_RATIO = 0.10`,原 0.15 取消)。
- 参与池 = **快捷栏内本模组便携元件的拔刀剑内容(优先)** → 再补**快捷栏内的拔刀剑**(排除手持那把,按物品同一性),**合计截断 8 把**。
- 元件**必须在快捷栏(0..8)**才计入;主背包不计。
- 容量 **8 条**,`insert` **每次新建条目**(同 NBT 不合并),非拔刀剑拒绝。

## 3. 文件清单

| 文件 | 作用 |
|---|---|
| `portable/PortableSlashCellItem.java` | 新物品:继承 AE2 便携基类,传 `MEStorageMenu.PORTABLE_ITEM_CELL_TYPE`;创造力/耐久等属性 |
| `portable/PortableSlashCellInventory.java` | 实现 `StorageCell`:8 条内容,键 = 刀身份(复用 `cell/BladeIdentity` 思路),持久化在物品组件 |
| `portable/PortableSlashCellHandler.java` | 实现 `ICellHandler`:`isCell` = 本物品 + (可选)继承/驱动语义;`getCellInventory` 返回上面的 inventory |
| `AppliedSlashComponents` | 新增 `portable_slash_cell_contents`(ItemStack 列表) |
| `AppliedSlashAe2` | 注册物品 + cell handler(沿用 `ModList.get().isLoaded("ae2")` 门卫) |
| `se/InventoryDamageTransferLogic` | 加伤池改造(见 §2)+ 签名缓存纳入元件内容哈希 + tooltip 明细行 |
| 资源 | 物品模型、16×16 贴图、`lang/zh_cn|en_us`、配方 JSON |
| `tools/resources.gradle` | `verifyResources` 新增断言 |
| `gametest/*` | 见 §4 |

## 4. 测试(新增)

`portableCellRejectsNonBlades` / `portableCellCapacityIsEight` / `portableCellNeverMergesIdenticalBlades` /
`poolCountsCellFirstThenHotbar` / `poolIsTenPercentEach` / `cellOutsideHotbarDoesNotCount` / `heldBladeExcludesItself` /
`bonusReachesAttribute`(扩展元件场景)

## 5. 边界

无电 ⇒ 走 AE2 原生行为;**加伤不以电量判定**;元件里放元件 ⇒ 拒绝;快捷栏多个元件 ⇒ 全部计入但总量仍截断 8;
刀被取走 ⇒ 下次签名变化即重算;升级卡首版不支持(与现有元件一致:避免"装了没效果")。