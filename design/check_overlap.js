// 元素间重叠自检 —— 段 1 的输出喂给段 2(因为我不能跑浏览器,只能靠"输出→回填"闭环)
// 用法:node check_overlap.js measure  → 打印需要回填的实测矩形
//       node check_overlap.js          → 用回填的数据做两两重叠检查
const fs = require('fs');
const path = require('path');

const designDir = __dirname;
const probePath = path.join(designDir, '_probe.html');
const dataPath = path.join(designDir, '_elements.json');
const htmlPath = path.join(designDir, 'blade_charger_mockup.html');

if (process.argv[2] === 'measure') {
  // 生成探测页:用*精确*文本宽度(而不是 getBoundingClientRect,那会受字体子集影响)
  const src = fs.readFileSync(htmlPath, 'utf8');
  const script = `
<script>
window.addEventListener('load', () => {
  const gui = document.getElementById('gui');
  const el = gui.getBoundingClientRect();
  const box = id => {
    const r = document.getElementById(id).getBoundingClientRect();
    const p = v => Math.round((v - el.left) * 1000) / 1000;
    const q = v => Math.round((v - el.top) * 1000) / 1000;
    return { x: p(r.left), y: q(r.top), w: Math.round(r.width * 1000) / 1000,
             h: Math.round(r.height * 1000) / 1000 };
  };
  // 槽内容的真实边界(图标 16×16 居中)
  const slotBox = (id) => {
    const r = document.getElementById(id).getBoundingClientRect();
    const p = v => Math.round((v - el.left) * 1000) / 1000;
    const q = v => Math.round((v - el.top) * 1000) / 1000;
    return { x: p(r.left) + 1, y: q(r.top) + 1, w: 16, h: 16 };
  };
  const data = {
    panel:   { x: 0, y: 0, w: Math.round(gui.offsetWidth), h: Math.round(gui.offsetHeight) },
    energyLabel: box('energyLabel'),
    energyValue: box('energyValue'),
    fuelLabel:   box('fuelLabel'),
    fuelValue:   box('fuelValue'),
    bufLabel:    box('bufLabel'),
    bufGauge:    box('bufGauge'),
    fuelGauge:   box('fuelGauge'),
    invLabel:    box('invLabel'),
    bladeIcon:   slotBox('bladeSlot'),
    fuelIcon:    slotBox('fuelSlot'),
  };
  document.title = 'PROBE:' + JSON.stringify(data);
  const pre = document.createElement('pre');
  pre.id = 'probe-out';
  pre.textContent = JSON.stringify(data, null, 2);
  document.body.appendChild(pre);
});
<\/script>`;
  fs.writeFileSync(probePath, src.replace('</body>', script + '\n</body>'), 'utf8');
  console.log('探测页已生成: ' + probePath);
  console.log('请用浏览器打开它,把页面里的 JSON(或标题 PROBE: 之后的内容)贴回来,');
  console.log('或直接把 <pre id="probe-out"> 的内容保存成 ' + dataPath);
  process.exit(0);
}

// ---- 段 2:两两重叠检查 ----
if (!fs.existsSync(dataPath)) {
  console.log('还没有实测数据:先跑 `node check_overlap.js measure`,把生成的探测页在浏览器里打开,');
  console.log('再把输出保存为 ' + dataPath + '。');
  process.exit(2);
}

const els = JSON.parse(fs.readFileSync(dataPath, 'utf8'));
const names = Object.keys(els).filter(k => k !== 'panel');
const overlap = (a, b) => {
  const ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
  const oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
  return (ox > 0 && oy > 0) ? { ox, oy } : null;
};

let bad = 0;
console.log('=== 两两重叠自检(面板 ' + els.panel.w + '×' + els.panel.h + ')===');
for (let i = 0; i < names.length; i++) {
  for (let j = i + 1; j < names.length; j++) {
    const a = els[names[i]], b = els[names[j]];
    const ov = overlap(a, b);
    if (ov) {
      bad++;
      console.log(`  [!!] ${names[i]} ∩ ${names[j]}  = ${ov.ox.toFixed(1)} × ${ov.oy.toFixed(1)} px`);
    }
  }
}
// 越界检查
for (const n of names) {
  const e = els[n];
  if (e.x < 0 || e.y < 0 || e.x + e.w > els.panel.w || e.y + e.h > els.panel.h) {
    bad++;
    console.log(`  [!!] ${n} 超出面板: x∈[${e.x}, ${(e.x + e.w).toFixed(1)}] y∈[${e.y}, ${(e.y + e.h).toFixed(1)}]`);
  }
}
console.log(bad === 0 ? '无重叠、无越界 ✓' : `${bad} 处问题 ✗`);
process.exit(bad === 0 ? 0 : 1);
