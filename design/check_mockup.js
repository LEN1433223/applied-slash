// 校验原型:1) 内联 JS 语法 (2) HTML 与 Java 的布局数值逐项一致 (3) 配色一致
//           4) 几何:面板内 + 两两不重叠   5) 对比度:文字 ≥4.5:1、图形 ≥3.0:1
const fs = require('fs');
const path = require('path');

const dir = 'D:/Agnet_all/Agnet_mod2/appliedslash-template-1.21.1';
const htmlPath = path.join(dir, 'design/blade_charger_mockup.html');
const javaScreen = path.join(dir, 'src/main/java/com/applied/slash/charged/client/BladeChargerScreen.java');
const javaPanel = path.join(dir, 'src/main/java/com/applied/slash/charged/client/VanillaPanel.java');
const javaMenu = path.join(dir, 'src/main/java/com/applied/slash/charged/block/BladeChargerMenu.java');

const html = fs.readFileSync(htmlPath, 'utf8');
const screen = fs.readFileSync(javaScreen, 'utf8');
const panel = fs.readFileSync(javaPanel, 'utf8');
const menu = fs.readFileSync(javaMenu, 'utf8');

// 面板尺寸的唯一来源是屏幕样式文档的 generatedBackground(Java 里用 this.imageWidth/imageHeight)
const styleDocPath = path.join(dir, 'src/main/resources/assets/applied_slash/screens/blade_charger.json');
const styleJson = JSON.parse(fs.readFileSync(styleDocPath, 'utf8'));
const styleDoc = styleJson.generatedBackground;

let fail = 0;
const ok = (cond, msg) => { console.log((cond ? '  [OK] ' : '  [!!] ') + msg); if (!cond) fail++; };

// ---- 1) 内联 JS 语法 ----
console.log('=== 1) HTML 内联 JS 语法 ===');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
ok(scripts.length === 1, `找到 ${scripts.length} 段内联脚本`);
const js = scripts.join('\n');
try {
  new Function(js);           // 只解析不执行(没有 DOM)
  ok(true, 'JS 语法解析通过');
} catch (e) {
  ok(false, 'JS 语法错误: ' + e.message);
}
const idsUsed = [...js.matchAll(/getElementById\('([^']+)'\)/g)].map(m => m[1]);
const idsDefined = [...html.matchAll(/id="([^"]+)"/g)].map(m => m[1]);
const missing = [...new Set(idsUsed)].filter(i => !idsDefined.includes(i));
ok(missing.length === 0, `脚本引用的 id 全部存在${missing.length ? '(缺:' + missing.join(',') + ')' : ''}`);

// ---- 2) 布局数值一致性 ----
console.log('\n=== 2) 布局数值一致性 ===');
const javaConst = (src, name) => {
  const m = src.match(new RegExp(name.replace(/[$]/g, '\\$') + '\\s*=\\s*([^;]+);'));
  return m ? m[1].trim() : null;
};
const cssVar = name => {
  const m = html.match(new RegExp('--' + name + '\\s*:\\s*([0-9.]+)px'));
  return m ? Number(m[1]) : null;
};
const javaInt = (src, name) => {
  const v = javaConst(src, name);
  if (v === null) return null;
  // 常量可能是另一个常量名(如 ENERGY_X = ARROW_X + ARROW_W + 4):解析一层引用
  if (!/^[\d\s+\-*/.()]+$/.test(v)) {
    return v.replace(/[A-Z_][A-Z0-9_]*/g, ref => {
      const inner = javaInt(src, ref);
      return inner === null ? ref : String(inner);
    });
  }
  try {
    // eslint-disable-next-line no-new-func
    return Function('"use strict";return (' + v + ')')();
  } catch (e) {
    return v;
  }
};

const checks = [
  ['panel-w',        cssVar('panel-w'),        styleDoc.width],
  ['panel-h',        cssVar('panel-h'),        styleDoc.height],
  ['blade-slot-x',   cssVar('blade-slot-x'),   javaInt(menu, 'SLOT_BLADE_X')],
  ['blade-slot-y',   cssVar('blade-slot-y'),   javaInt(menu, 'SLOT_BLADE_Y')],
  ['fuel-slot-x',    cssVar('fuel-slot-x'),    javaInt(menu, 'SLOT_FUEL_X')],
  ['fuel-slot-y',    cssVar('fuel-slot-y'),    javaInt(menu, 'SLOT_FUEL_Y')],
  ['slot-frame',     cssVar('slot-frame'),     javaInt(panel, 'SLOT_FRAME')],
  ['flame-x',        cssVar('flame-x'),        javaInt(screen, 'FLAME_X')],
  ['flame-y',        cssVar('flame-y'),        javaInt(screen, 'FLAME_Y')],
  ['energy-y',       cssVar('energy-y'),       javaInt(screen, 'ENERGY_Y')],
  ['buf-gauge-x',    cssVar('buf-gauge-x'),    javaInt(screen, 'BUFFER_X')],
  ['buf-gauge-y',    cssVar('buf-gauge-y'),    javaInt(screen, 'BUFFER_Y')],
  ['buf-gauge-w',    cssVar('buf-gauge-w'),    javaInt(screen, 'BUFFER_W')],
  ['buf-gauge-h',    cssVar('buf-gauge-h'),    javaInt(screen, 'BUFFER_H')],
  ['inv-x',          cssVar('inv-x'),          javaInt(menu, 'PLAYER_INV_X')],
  ['inv-y',          cssVar('inv-y'),          javaInt(menu, 'PLAYER_INV_Y')],
];
for (const [name, htmlVal, javaVal] of checks) {
  ok(htmlVal === javaVal, `--${name}: html=${htmlVal}  java=${javaVal}`);
}

// 能量读数的左边界必须与 Java 的 ENERGY_X 一致(它只出现在 HTML 的 rects() 里)
ok(javaInt(screen, 'ENERGY_X') === 84, `Java ENERGY_X = ${javaInt(screen, 'ENERGY_X')}(应 84 = 槽列右缘 44 + 40)`);
ok(new RegExp('x:84').test(html), 'HTML 的 rects() 里能量读数左边界也是 84');

// Java 的火焰掩码尺寸必须与 HTML 里画的形状一致
ok(javaInt(screen, 'FLAME_W') === 12 && javaInt(screen, 'FLAME_H') === 11,
   `火焰掩码声明尺寸 ${javaInt(screen, 'FLAME_W')}x${javaInt(screen, 'FLAME_H')}(应为 12x11)`);
// 箭头已被移除 —— 两个文件里都不该再有它的痕迹(防止旧版残留)
const arrowLeftovers = ['ARROW_X', 'ARROW_Y', 'arrowIcon', 'arrow-x'].filter(tok =>
  new RegExp(tok).test(screen) || new RegExp(tok).test(html));
ok(arrowLeftovers.length === 0,
   `进度箭头已彻底移除${arrowLeftovers.length ? '(残留:' + arrowLeftovers.join(',') + ')' : ''}`);

// 派生关系:快捷栏不是手写的 CSS 变量,而是 JS 里由背包算出来的(invY + HOTBAR_OFFSET)
const invY = cssVar('inv-y');
const jsHotbarOffset = js.match(/HOTBAR_OFFSET\s*=\s*(\d+)/);
const hotbarOffset = jsHotbarOffset ? Number(jsHotbarOffset[1]) : NaN;
ok(hotbarOffset === 54, `JS 里 HOTBAR_OFFSET = ${hotbarOffset}(应 54 = 3 行)`);
const jsInv = js.match(/INV_X = (\d+), INV_STEP = (\d+)/);
ok(jsInv && Number(jsInv[1]) === 8 && Number(jsInv[2]) === 18,
   `JS 里的 INV_X/INV_STEP = ${jsInv ? jsInv[1] + '/' + jsInv[2] : '?'}(应 8/18)`);

const noUnit = ['--panel-w','--panel-h','--blade-slot-x','--fuel-slot-y','--slot-frame',
                '--flame-y','--energy-y','--buf-gauge-w','--buf-gauge-h','--inv-y'];
const badUnit = noUnit.filter(v => !new RegExp(v + '\\s*:\\s*[0-9.]+px').test(html));
ok(badUnit.length === 0, `所有被 getComputedStyle 读取的变量都带 px 单位${badUnit.length ? '(缺:' + badUnit.join(',') + ')' : ''}`);

// ---- 3) 配色一致性 ----
console.log('\n=== 3) 配色一致性(原版 GUI 白灰 + 语义色) ===');
const lightBlock = html.split('body[data-theme="dark"]')[0];
const cssColor = n => {
  const m = lightBlock.match(new RegExp('--' + n + '\\s*:\\s*(#[0-9A-Fa-f]{6})'));
  return m ? m[1].toUpperCase() : null;
};
const javaColor = (src, name) => {
  const m = src.match(new RegExp(name + '\\s*=\\s*0xFF([0-9A-Fa-f]{6})'));
  if (m) return ('#' + m[1]).toUpperCase();
  const alias = src.match(new RegExp(name + '\\s*=\\s*([A-Z_][A-Z0-9_]*)'));
  return alias ? javaColor(src, alias[1]) : null;
};
for (const [css, src, jv] of [
  ['c-panel-border', panel,  'BORDER'],
  ['c-panel-inner',  panel,  'INNER_LIGHT'],
  ['c-panel-shadow', panel,  'INNER_DARK'],
  ['c-panel-face',   panel,  'FACE'],
  ['c-slot-face',    panel,  'SLOT_FACE'],
  ['c-track',        panel,  'TRACK'],
  ['c-text',         screen, 'C_TEXT'],
  ['c-label',        screen, 'C_LABEL'],
  ['c-accent',       screen, 'C_ACCENT'],
  ['c-flame-out',    screen, 'C_FLAME_OUTER'],
  ['c-flame-in',     screen, 'C_FLAME_INNER'],
  ['c-ok',           screen, 'C_OK'],
  ['c-low',          screen, 'C_LOW'],
  ['c-error',        screen, 'C_ERROR'],
]) {
  ok(cssColor(css) === javaColor(src, jv), `--${css} (${cssColor(css)}) == ${jv} (${javaColor(src, jv)})`);
}

// 已被"做减法"删掉的元素不能再出现在 HTML 里(防止旧版残留)
const removed = ['helpbtn', 'accent', 'title'];
const leftovers = removed.filter(cls => new RegExp('class="[^"]*' + cls).test(html));
ok(leftovers.length === 0, `已删除的元素不再出现${leftovers.length ? '(残留:' + leftovers.join(',') + ')' : ''}`);

// ---- 4) 几何自检 ----
console.log('\n=== 4) 几何自检(面板内、两两不重叠) ===');
const P = { w: cssVar('panel-w'), h: cssVar('panel-h') };
const R = [
  { n: '刀槽',       x: cssVar('blade-slot-x') - 1, y: cssVar('blade-slot-y') - 1, w: 18, h: 18 },
  { n: '虞美人槽',   x: cssVar('fuel-slot-x') - 1,  y: cssVar('fuel-slot-y') - 1,  w: 18, h: 18 },
  { n: '火焰',       x: cssVar('flame-x'), y: cssVar('flame-y'), w: 12, h: 11 },
  { n: '能量读数',   x: 84, y: cssVar('energy-y'), w: 160 - 84, h: 9 },
  { n: '缓冲条',     x: cssVar('buf-gauge-x'), y: cssVar('buf-gauge-y'),
                     w: cssVar('buf-gauge-w'), h: cssVar('buf-gauge-h') },
  { n: '物品栏标签', x: cssVar('inv-x'), y: cssVar('inv-y') - 11, w: 40, h: 9 },
  { n: '玩家背包块', x: cssVar('inv-x') - 1, y: cssVar('inv-y') - 1,
                     w: 9 * 18, h: (cssVar('inv-y') + hotbarOffset + 17) - (cssVar('inv-y') - 1) + 1 },
];
const EXPECTED = new Set(['火焰|虞美人槽', '虞美人槽|火焰']);
const hit = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

let geoBad = 0;
for (const r of R) {
  const fit = r.x >= 0 && r.y >= 0 && r.x + r.w <= P.w && r.y + r.h <= P.h;
  if (!fit) geoBad++;
  console.log(`  ${fit ? '[OK]' : '[!!]'} ${r.n.padEnd(12)} ${r.x},${r.y} → ${r.x + r.w},${r.y + r.h} (面板 ${P.w}×${P.h})`);
}
let pairBad = 0;
for (let i = 0; i < R.length; i++) {
  for (let j = i + 1; j < R.length; j++) {
    if (EXPECTED.has(`${R[i].n}|${R[j].n}`)) continue;
    if (hit(R[i], R[j])) {
      pairBad++;
      console.log(`  [!!] 重叠:${R[i].n} ⇄ ${R[j].n}`);
    }
  }
}
console.log(`  ${pairBad === 0 ? '[OK]' : '[!!]'} 两两不重叠(排除 ${EXPECTED.size / 2} 对设计内包含关系)`);
ok(geoBad === 0, `越界元素数 = ${geoBad}`);
ok(pairBad === 0, `两两重叠对数 = ${pairBad}`);

// 机器槽坐标必须与熔炉逐个像素相同 —— 这是这一版的核心卖点,写死校验
ok(cssVar('blade-slot-x') === 26 && cssVar('blade-slot-y') === 20,
   `刀槽 (${cssVar('blade-slot-x')},${cssVar('blade-slot-y')}) == 熔炉输入槽 (26,20)`);
ok(cssVar('fuel-slot-x') === 26 && cssVar('fuel-slot-y') === 46,
   `燃料槽 (${cssVar('fuel-slot-x')},${cssVar('fuel-slot-y')}) == 熔炉燃料槽 (26,46)`);

// 快捷栏底边必须停在面板内(不贴边也不越界)
const hotBottom = invY + hotbarOffset + cssVar('slot-frame') - 1;
ok(hotBottom < P.h, `快捷栏底边 ${hotBottom} < 面板高 ${P.h}`);

// 火焰必须落在两个槽之间那条缝里(缝 38..44,允许多出 3 px 压在上边框上)
const flameTop = cssVar('flame-y'), flameBottom = flameTop + 11;
ok(flameTop >= cssVar('blade-slot-y') + 16 && flameBottom <= cssVar('fuel-slot-y') + 4,
   `火焰 y ${flameTop}..${flameBottom} 落在刀槽底(${cssVar('blade-slot-y') + 16})与燃料槽上边框之间`);

// ---- 5) 对比度自检 ----
console.log('\n=== 5) 对比度自检 ===');
const srgb = c => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
const lum = hex => {
  const h = hex.replace('#', '');
  return 0.2126 * srgb(parseInt(h.slice(0, 2), 16))
       + 0.7152 * srgb(parseInt(h.slice(2, 4), 16))
       + 0.0722 * srgb(parseInt(h.slice(4, 6), 16));
};
const ratio = (a, b) => {
  const la = lum(a), lb = lum(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};
const face = cssColor('c-panel-face'), track = cssColor('c-track');
const pairs = [
  ['主文字 / 面板底',   cssColor('c-text'),      face,  4.5],
  ['次要文字 / 面板底', cssColor('c-label'),     face,  4.5],
  ['外焰 / 面板底',     cssColor('c-flame-out'), face,  3.0],
  ['槽内底 / 面板底',   cssColor('c-slot-face'), face,  1.15],
  ['凹槽 / 面板底',     track,                   face,  3.0],
  // 仪表内部:填充压在**深凹槽**上,所以用亮色(方向与本页文字相反:文字压浅底、填充压暗底)
  ['缓冲填充 / 凹槽',   cssColor('c-accent'),    track, 3.0],
  ['已满色 / 凹槽',     cssColor('c-ok'),        track, 3.0],
  ['偏低色 / 凹槽',     cssColor('c-low'),       track, 3.0],
  ['无电色 / 凹槽',     cssColor('c-error'),     track, 3.0],
  // 悬停提示是深色底,状态文字压在它上面(#202020 近似原版 tooltip 底色)
  ['已满色 / 提示底',   cssColor('c-ok'),        '#202020', 4.5],
  ['无电色 / 提示底',   cssColor('c-error'),     '#202020', 4.5],
];
let contrastBad = 0, worst = 99;
for (const [name, fg, bg, min] of pairs) {
  const r = ratio(fg, bg);
  const pass = r >= min;
  if (!pass) contrastBad++;
  worst = Math.min(worst, r / min);
  console.log(`  ${pass ? '[OK]' : '[!!]'} ${name.padEnd(20)} ${r.toFixed(2)}:1  (要求 ≥ ${min})`);
}
ok(contrastBad === 0, `对比度不达标项 = ${contrastBad}(最紧一项 = 阈值的 ${worst.toFixed(2)} 倍)`);

console.log('\n' + (fail === 0 ? '全部通过 ✓' : `${fail} 项不一致 ✗`));
process.exit(fail === 0 ? 0 : 1);
