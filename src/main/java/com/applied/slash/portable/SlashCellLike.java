package com.applied.slash.portable;

/**
 * 标记接口:**手持 Slash 元件**。
 *
 * <p>存在的唯一理由:SE 的加伤逻辑必须能识别"快捷栏里哪些格子是元件",但**不能引用任何 AE2 类型**
 * (否则 AE2 缺席时那条路径会 NoClassDefFoundError)。本接口不含任何 AE2 引用,
 * 由 {@code PortableSlashCellItem} 实现;SE 逻辑只依赖本接口与 {@link SlashCellAccess}。
 */
public interface SlashCellLike {
}