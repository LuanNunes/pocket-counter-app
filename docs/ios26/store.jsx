// In-memory store for the iOS prototype (mirrors the Android data model).
const PC_MONTHS = ["2026-01", "2026-02", "2026-03", "2026-04", "2026-05"];
const PC_TODAY = "2026-05-19";
const PC_CUR = "2026-05";
const MES = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"];
const MES_C = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
const monthName = (mk) => cap(MES[+mk.slice(5, 7) - 1]);
const monthShort = (mk) => MES_C[+mk.slice(5, 7) - 1];
const dayLabel = (d) => +d.slice(8, 10) + " de " + MES[+d.slice(5, 7) - 1];
const ddmm = (d) => d ? d.slice(8, 10) + "/" + d.slice(5, 7) : "—";
function fmt(v) { return Number(v).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
const fmtK = (v) => Math.abs(v) >= 1000 ? (v / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + "k" : String(Math.round(v));

function seeded(n) { let s = n; return () => { s = (s * 9301 + 49297) % 233280; return s / 233280; }; }
function genMonth(mk, i) {
  const r = seeded(i * 13 + 5);
  const v = (a, b) => Math.round((a + (b - a) * r()) * 100) / 100;
  let k = 0;
  const T = (day, name, amount, tag, pm, card, extra) => {
    const date = mk + "-" + String(day).padStart(2, "0");
    return { id: mk + "-" + (k++), date, name, amount, type: amount > 0 ? "income" : "expense", tagIds: tag ? [tag] : [], paymentMethod: pm, cardId: card || null, status: date > PC_TODAY ? "PENDING" : "PAID", fixo: false, ...(extra || {}) };
  };
  const a = [
    T(5, "Salário", 6450, "tag-salario", "debit", null, { fixo: true }),
    T(10, "Aluguel", -2400, "tag-aluguel", "debit", null, { fixo: true }),
    T(19, "Enel — energia", -v(205, 248), "tag-contas-casa", "pix", null, { fixo: true }),
    T(15, "Spotify", -21.9, "tag-streaming", "credit", "nubank", { fixo: true }),
    T(3, "Smart Fit", -99.9, "tag-academia", "credit", "itau", { fixo: true }),
    T(7, "Pão de Açúcar", -v(230, 340), "tag-supermercado", "credit", "itau"),
    T(21, "Carrefour", -v(160, 300), "tag-supermercado", "debit"),
    T(12, "Coco Bambu", -v(90, 190), "tag-restaurante", "credit", "nubank"),
    T(24, "iFood", -v(45, 130), "tag-delivery", "credit", "nubank"),
    T(9, "Uber", -v(18, 42), "tag-app-mobil", "credit", "nubank"),
    T(17, "99", -v(14, 32), "tag-app-mobil", "credit", "nubank"),
    T(14, "Posto Shell", -v(170, 290), "tag-combustivel", "debit"),
  ];
  if (r() > .35) a.push(T(18, "Drogasil", -v(35, 120), "tag-farmacia", "pix"));
  if (i % 2 === 0) a.push(T(20, "Freela Studio Z", v(900, 1900), "tag-freelance", "pix"));
  if (i === 1) a.push(T(11, "Cinemark", -64, "tag-cinema", "credit", "nubank"));
  if (i === 2) a.push(T(26, "Magazine Luiza", -v(420, 900), "tag-eletronicos", "credit", "nubank"));
  if (i === 3) a.push(T(22, "Booking.com", -980, "tag-viagem", "credit", "itau"));
  if (i === 4) a.push(T(6, "Dividendos XP", 125, null, "pix"));
  return a;
}
function seedLedger() {
  const o = {};
  PC_MONTHS.forEach((mk, i) => {
    let a = genMonth(mk, i);
    if (mk === PC_CUR) a = a.filter((t) => !["Uber", "Pão de Açúcar"].includes(t.name)).concat(window.SAMPLE_HISTORY.map(window.legacyTx).map((t) => ({ ...t, status: t.status || "PAID" })));
    o[mk] = a.sort((x, y) => y.date.localeCompare(x.date));
  });
  return o;
}

function usePC() {
  const [ledger, setLedger] = React.useState(seedLedger);
  const [contexts, setContexts] = React.useState(() => window.SAMPLE_CONTEXTS.map((c) => ({ ...c })));
  const [tags, setTags] = React.useState(() => window.SAMPLE_TAGS.map((t) => ({ ...t })));
  const cards = React.useMemo(() => window.SAMPLE_CARDS, []);
  window.PC_TAGS = tags; window.PC_CTX = contexts;
  const uid = () => "u" + Date.now().toString(36) + Math.floor(Math.random() * 999);
  const addTx = (mk, tx) => { const id = uid(); setLedger((l) => ({ ...l, [mk]: [{ ...tx, id }, ...(l[mk] || [])] })); return id; };
  const updateTx = (mk, id, p) => setLedger((l) => ({ ...l, [mk]: (l[mk] || []).map((t) => t.id === id ? { ...t, ...p } : t) }));
  const removeTx = (mk, id) => setLedger((l) => ({ ...l, [mk]: (l[mk] || []).filter((t) => t.id !== id) }));
  const reorder = (mk, ids) => setLedger((l) => {
    const arr = l[mk] || []; const set = new Set(ids); const by = Object.fromEntries(arr.map((t) => [t.id, t]));
    const slots = []; arr.forEach((t, i) => { if (set.has(t.id)) slots.push(i); });
    const out = arr.slice(); slots.forEach((s, j) => { out[s] = by[ids[j]]; });
    return { ...l, [mk]: out };
  });
  const addContext = (name, color) => { const id = "ctx-" + uid(); setContexts((c) => [...c, { id, name, color, kind: "expense" }]); return id; };
  const removeContext = (id) => { setContexts((c) => c.filter((x) => x.id !== id)); setTags((t) => t.filter((x) => x.idContext !== id)); };
  const addTag = (name, idContext, kind) => setTags((t) => [...t, { id: "tag-" + uid(), name, idContext: idContext || null, ...(kind === "income" ? { kind: "income", color: "oklch(0.6 0.13 200)" } : {}) }]);
  const removeTag = (id) => setTags((t) => t.filter((x) => x.id !== id));
  return { ledger, contexts, tags, cards, addTx, updateTx, removeTx, reorder, addContext, removeContext, addTag, removeTag };
}

const NEUTRAL = "rgba(142,142,147,.9)";
const tagOf = (id) => (window.PC_TAGS || []).find((t) => t.id === id);
const ctxOf = (tag) => tag && (window.PC_CTX || []).find((c) => c.id === tag.idContext);
const tagColor = (id) => { const t = tagOf(id); if (!t) return NEUTRAL; return t.color || ((ctxOf(t) || {}).color) || NEUTRAL; };
function payLabel(cards, t) {
  if (!t || !t.paymentMethod) return null;
  if (t.paymentMethod === "credit") { const c = cards.find((x) => x.id === t.cardId); return c ? c.name.replace(/^Cartão /, "") : "Crédito"; }
  return window.METHOD_LABEL[t.paymentMethod];
}
const isInc = (t) => t.type === "income" || t.amount > 0;
function totals(list) {
  let inc = 0, exp = 0, pend = 0, pc = 0;
  for (const t of list) { const a = Math.abs(t.amount); if (isInc(t)) inc += a; else exp += a; if (t.status === "PENDING") { pend += a; pc++; } }
  return { inc, exp, bal: inc - exp, pend, pendCount: pc };
}

Object.assign(window, { PC_MONTHS, PC_TODAY, PC_CUR, MES, MES_C, monthName, monthShort, dayLabel, ddmm, fmt, fmtK, usePC, tagOf, ctxOf, tagColor, payLabel, isInc, totals, NEUTRAL });
