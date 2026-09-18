# 第三方资源与代码声明 (Third-Party Notices)

本模组分发物中包含下列第三方作品。**两份素材都经过修改**(模型裁剪 + 贴图重新着色,见下方"修改声明")。

---

## 1. Slashblade-Murasame(丛雨丸)的刀身模型与贴图

- 来源仓库:<https://github.com/sangeeeee/Slashblade-Murasame>
- 原始文件:
  - `src/main/java/cn/adwadg/murasame/events/src/main/resources/assets/murasame/models/murasame/murasamemaru.obj`
  - `src/main/java/cn/adwadg/murasame/events/src/main/resources/assets/murasame/models/murasame/murasamemaru.png`
- 本模组中的副本:
  - `src/main/resources/assets/applied_slash/models/lili/lili.obj`
  - `src/main/resources/assets/applied_slash/models/lili/lili.png`
- 用途:拔刀剑「莉莉」(`applied_slash:lili`)的刀身模型与贴图(设计完全复用丛雨丸)。
- **修改声明(modified)**:
  - `lili.obj` —— **经修改:删掉了鞘上的配件面集**(两个金属环 + 栗形 + 下绪挂绳,共 301 面;只保留
    跨度为整根鞘的壳 56 面)。**几何与 UV 的其余部分与原始素材完全一致**(不做圆柱重建、不做 UV 重映射)。
    原始文件存档在 `art/lili_source.obj`,裁剪由 `gradlew generateLiliMesh` 生成(`-PliliRestoreMesh` 一键还原),
    范围由 `gradlew verifyResources` 断言(其余 7 个组的面数与源存档**逐组相等**;鞘组 1 个连通块、X 跨度 ≥ 250)。
  - `lili.png` —— **经重绘**:底图 = 原始素材 **2× 最近邻放大**(UV 一个都不动),只把**鞘用到的像素**换成
    AI 生成的粉黛花纹(横向镜像拼接保证绕鞘一周无缝)+ 程序画的两端金箍。原始像素存档在 `art/lili_source.png`,
    生成由 `gradlew generateLiliTexture`(参数 `-PsayaArt/-PsayaArtCropBottom`,`-PsayaRestore` 一键还原),
    改动范围由 `gradlew verifyResources` 断言(掩码外零改动、与刀身共用像素零改动、没有任何组比原素材更多地采到透明像素)。
  - 依据 MIT 许可,原作品允许修改与再分发;此处保留原始版权声明并注明修改事实。

```
MIT License

Copyright (c) 2025 CeliaClaire

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 2. 刀鞘纹样(AI 生成,用户提供)

- 来源:用户提供的 AI 生成图(工作副本 `art/saya_ai.png`,864×4576;**该原图不入库**)。
- 用途:鞘(`sheath` 组)的纹样 —— 粉黛色调 + 花瓣/爱心,由 `gradlew generateLiliTexture` 导入
  (裁掉原图底部 110 px 的水印、镜像拼接做到横向无缝、缩放到鞘的 UV 区域 181×957)。
- 生成提示词与全部约束见 `README.md` 的「鞘纹样的 AI 提示词」一节。
- 说明:AI 生成内容的权利归属按其生成服务条款处理;本仓库只分发**生成后的贴图产物**
  (`src/main/resources/assets/applied_slash/models/lili/lili.png`)与生成脚本,不分发 AI 原图。

---

## 3. SlashBlade: Resharped(重锋)

本模组是**附属模组**,不复制、不打包重锋的任何美术资源;运行时通过数据包注册表
(`slashblade:named_blades`)、物品/能力/事件 API 与重锋交互。重锋本体及其美术资源的授权
归其作者所有(见其自身发布页),本模组只做引用与联动。

## 4. Applied Energistics 2

本模组的存储元件与充能方块通过 AE2 公开 API(`appeng.api.*`)接入,不复制其资源。
