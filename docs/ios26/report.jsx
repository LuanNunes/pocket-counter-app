// Relatório — Visão geral (categoria/fonte) + Por tag (ranking com gráfico mensal, linhas, mapa de calor).
const INC_PAL = ["oklch(0.6 0.14 155)", "oklch(0.55 0.14 250)", "oklch(0.64 0.14 70)", "oklch(0.58 0.15 320)", "oklch(0.6 0.12 200)", "oklch(0.55 0.1 30)"];
function periodKeys(period, anchor) {
  const i = PC_MONTHS.indexOf(anchor);
  if (period === "mes") return [anchor];
  if (period === "tri") return PC_MONTHS.slice(Math.max(0, i - 2), i + 1);
  return PC_MONTHS.filter((m) => m.slice(0, 4) === anchor.slice(0, 4) && m <= anchor);
}
function rangeLabel(keys) { return keys.length === 1 ? monthName(keys[0]) + " " + keys[0].slice(0, 4) : monthShort(keys[0]) + " – " + monthShort(keys[keys.length - 1]) + " " + keys[0].slice(0, 4); }
// dim: "cat" (categoria / fonte) | "tag"
function aggregate(pc, keys, kind, dim) {
  const map = new Map();
  keys.forEach((mk, mi) => {
    for (const t of pc.ledger[mk] || []) {
      if ((kind === "income") !== isInc(t)) continue;
      const tag = tagOf((t.tagIds || [])[0]);
      let key, name, color, ctxName = null, ord = 0;
      if (kind === "income") { key = "in:" + t.name; name = t.name; }
      else if (dim === "cat") { const c = ctxOf(tag); key = c ? c.id : "_none"; name = c ? c.name : "Sem categoria"; color = c ? c.color : NEUTRAL; }
      else { const c = ctxOf(tag); key = tag ? tag.id : "_none"; name = tag ? tag.name : "Sem tag"; color = tag ? tagColor(tag.id) : NEUTRAL; ctxName = c ? c.name : null; }
      if (!map.has(key)) map.set(key, { key, name, color, ctxName, vals: keys.map(() => 0), total: 0 });
      const g = map.get(key); g.vals[mi] += Math.abs(t.amount); g.total += Math.abs(t.amount);
    }
  });
  const out = [...map.values()].sort((a, b) => b.total - a.total);
  out.forEach((g, i) => {
    if (!g.color) g.color = INC_PAL[i % INC_PAL.length];
    g.avg = g.total / keys.length; g.active = g.vals.filter((v) => v > 0).length;
    const n = g.vals.length; g.delta = n < 2 ? undefined : g.vals[n - 2] > 0 ? (g.vals[n - 1] - g.vals[n - 2]) / g.vals[n - 2] : null;
  });
  return out;
}

function Donut({ data, total, label }) {
  const R = 74, C = 2 * Math.PI * R; let off = 0;
  return (
    <div className="donut">
      <svg width="190" height="190" viewBox="0 0 190 190">
        <circle cx="95" cy="95" r={R} fill="none" stroke="var(--fill)" strokeWidth="22" />
        {data.map((d) => { const len = (d.total / total) * C; const el = <circle key={d.key} cx="95" cy="95" r={R} fill="none" stroke={d.color} strokeWidth="22" strokeDasharray={Math.max(0, len - 2) + " " + C} strokeDashoffset={-off} transform="rotate(-90 95 95)" />; off += len; return el; })}
      </svg>
      <div className="donut-c"><small>{label}</small><b className="tnum">R$ {fmtK(total)}</b></div>
    </div>
  );
}
function Spark({ vals, color, w = 54, h = 20 }) {
  const mx = Math.max(...vals, 1); if (vals.length < 2) return <span style={{ width: w }} />;
  const pts = vals.map((v, i) => (i / (vals.length - 1)) * w + "," + (h - 2 - (v / mx) * (h - 4))).join(" ");
  return <svg width={w} height={h} className="spark"><polyline points={pts} fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function Delta({ d, kind }) {
  if (d === undefined) return null;
  if (d === null) return <span className="delta new">novo</span>;
  const up = d > 0.005, dn = d < -0.005; const bad = kind === "income" ? dn : up;
  return <span className={"delta " + (up || dn ? (bad ? "bad" : "good") : "")}>{up ? "↑" : dn ? "↓" : "="}{Math.abs(Math.round(d * 100))}%</span>;
}
function StackBars({ data, keys }) {
  const tot = keys.map((_, i) => data.reduce((s, d) => s + d.vals[i], 0)); const mx = Math.max(...tot, 1);
  return (
    <div className="sbars">
      {keys.map((k, i) => (
        <div key={k} className="sb-col">
          <span className="sb-v tnum">{fmtK(tot[i])}</span>
          <div className="sb-stack" style={{ height: (tot[i] / mx) * 88 + "%" }}>{data.map((d) => d.vals[i] > 0 && <i key={d.key} style={{ flex: d.vals[i], background: d.color }} />)}</div>
          <span className="sb-l">{monthShort(k)}</span>
        </div>
      ))}
    </div>
  );
}
function Lines({ series, keys, h = 170 }) {
  const W = 330, P = 14; const mx = Math.max(1, ...series.flatMap((s) => s.vals));
  const x = (i) => keys.length === 1 ? W / 2 : P + (i / (keys.length - 1)) * (W - P * 2); const y = (v) => h - 22 - (v / mx) * (h - 40);
  return (
    <svg width="100%" viewBox={"0 0 " + W + " " + h} className="lines">
      {[0, .5, 1].map((f) => <line key={f} x1="0" x2={W} y1={y(mx * f)} y2={y(mx * f)} stroke="var(--sep)" strokeDasharray={f ? "3 4" : ""} />)}
      {series.map((s) => <g key={s.key}><polyline points={s.vals.map((v, i) => x(i) + "," + y(v)).join(" ")} fill="none" stroke={s.color} strokeWidth="2.4" strokeLinejoin="round" strokeLinecap="round" />{s.vals.map((v, i) => <circle key={i} cx={x(i)} cy={y(v)} r="3" fill={s.color} />)}</g>)}
      {keys.map((k, i) => <text key={k} x={x(i)} y={h - 4} textAnchor="middle" className="ax">{monthShort(k)}</text>)}
    </svg>
  );
}
function MonthCols({ g, keys }) {
  const mx = Math.max(...g.vals, 1); const avg = Math.min(g.avg / mx, 1) * 88;
  return (
    <div className="mcols">
      <div className="mc-plot">
        <div className="mc-avg" style={{ bottom: avg + "%" }}><em>média</em></div>
        {g.vals.map((v, i) => { const peak = v > 0 && v === mx; return (
          <div key={i} className={"mc-col" + (peak ? " peak" : "")} title={monthShort(keys[i]) + " · " + (v ? "R$ " + fmt(v) : "sem lançamentos")}>
            {v > 0 && <span className="mc-v tnum" style={{ bottom: "calc(" + Math.max(2.5, (v / mx) * 88) + "% + 3px)" }}>{fmtK(v)}</span>}
            <i style={{ height: v > 0 ? Math.max(2.5, (v / mx) * 88) + "%" : 0, background: g.color }} />
          </div>); })}
      </div>
      <div className="mc-labs">{keys.map((k) => <span key={k}>{monthShort(k)}</span>)}</div>
    </div>
  );
}

function OverviewReport({ pc, keys, kind }) {
  const data = aggregate(pc, keys, kind, "cat"); const total = data.reduce((s, d) => s + d.total, 0);
  const [chart, setChart] = React.useState(() => localStorage.getItem("pc-ios-chart") || "bar");
  const setC = (v) => { setChart(v); localStorage.setItem("pc-ios-chart", v); };
  const other = aggregate(pc, keys, kind === "income" ? "expense" : "income", "cat").reduce((s, d) => s + d.total, 0);
  const bal = kind === "income" ? total - other : other - total;
  if (!data.length) return <div className="empty">Sem lançamentos no período.</div>;
  return (
    <>
      {keys.length > 1 && (
        <div className="kpis">
          <div className="kpi"><small>{kind === "income" ? "Receitas" : "Despesas"}</small><b className="tnum">R$ {fmt(total)}</b><span>média R$ {fmtK(total / keys.length)}/mês</span></div>
          <div className="kpi"><small>Saldo</small><b className={"tnum " + (bal >= 0 ? "inc" : "")}>{bal < 0 ? "−" : ""}R$ {fmt(Math.abs(bal))}</b><span>{keys.length} meses</span></div>
        </div>
      )}
      <div className="rcard">
        {keys.length > 1 && <div style={{ marginBottom: 14 }}><Seg sm value={chart} onChange={setC} options={[["bar", "Barras"], ["line", "Linhas"], ["pie", "Pizza"]]} /></div>}
        {keys.length === 1 || chart === "pie" ? <Donut data={data} total={total} label={kind === "income" ? "Recebido" : "Gasto"} />
          : chart === "bar" ? <StackBars data={data} keys={keys} /> : <Lines series={data.slice(0, 6)} keys={keys} />}
        <div className="legend">{data.slice(0, 6).map((d) => <span key={d.key}><span className="dot" style={{ background: d.color }} />{d.name}</span>)}</div>
      </div>
      <div className="sec-h">{kind === "income" ? "De onde veio" : "Onde você gastou"}<small>{data.length} {kind === "income" ? "fontes" : "categorias"}</small></div>
      <div className="list">
        {data.map((d) => (
          <div key={d.key} className="row rk-row">
            <span className="dot" style={{ background: d.color, width: 10, height: 10 }} />
            <div className="rt"><div className="rk-n">{d.name}</div><div className="rk-bar"><i style={{ width: (d.total / data[0].total) * 100 + "%", background: d.color }} /></div></div>
            {keys.length > 1 && <Spark vals={d.vals} color={d.color} />}
            <div className="rk-r"><b className="tnum">R$ {fmt(d.total)}</b><span>{Math.round((d.total / total) * 100)}%{keys.length > 1 && <> · <Delta d={d.delta} kind={kind} /></>}</span></div>
          </div>
        ))}
      </div>
    </>
  );
}

function TagReport({ pc, keys, kind }) {
  const [view, setView] = React.useState(() => localStorage.getItem("pc-ios-tagview") || "rank");
  const setV = (v) => { setView(v); localStorage.setItem("pc-ios-tagview", v); };
  const [cats, setCats] = React.useState([]);
  const [open, setOpen] = React.useState(null);
  const [hidden, setHidden] = React.useState({});
  let data = aggregate(pc, keys, kind, "tag");
  if (kind === "expense" && cats.length) data = data.filter((d) => cats.includes(d.ctxName));
  const total = data.reduce((s, d) => s + d.total, 0);
  const top = data[0];
  const mxCell = Math.max(1, ...data.flatMap((d) => d.vals));
  return (
    <>
      <div className="kpis">
        <div className="kpi"><small>{kind === "income" ? "Receitas" : "Despesas"} filtradas</small><b className="tnum">R$ {fmt(total)}</b><span>média R$ {fmtK(total / keys.length)}/mês</span></div>
        <div className="kpi"><small>Maior {kind === "income" ? "fonte" : "tag"}</small><b className="ell">{top ? top.name : "—"}</b><span className="tnum">{top ? "R$ " + fmt(top.total) + " · " + Math.round((top.total / total) * 100) + "%" : ""}</span></div>
      </div>
      {kind === "expense" && (
        <div className="hscroll" style={{ marginBottom: 12 }}>
          <button className={"chip" + (!cats.length ? " on" : "")} onClick={() => setCats([])}>Todas</button>
          {pc.contexts.map((c) => { const on = cats.includes(c.name); return <button key={c.id} className={"chip" + (on ? " on" : "")} onClick={() => setCats(on ? cats.filter((x) => x !== c.name) : [...cats, c.name])}><span className="dot" style={{ background: on ? "#fff" : c.color }} />{c.name}</button>; })}
        </div>
      )}
      <div className="pad" style={{ marginBottom: 12 }}><Seg sm value={view} onChange={setV} options={[["rank", "Ranking"], ["lines", "Linhas"], ["heat", "Mapa de calor"]]} /></div>
      {!data.length && <div className="empty">Nenhuma tag com esses filtros.</div>}
      {view === "rank" && data.length > 0 && (
        <div className="list">
          {data.map((d) => (
            <div key={d.key} className="tg-rr">
              <button className="row rk-row" onClick={() => setOpen(open === d.key ? null : d.key)}>
                <span className="dot" style={{ background: d.color, width: 10, height: 10 }} />
                <div className="rt"><div className="rk-n">{d.name}</div>{d.ctxName && <div className="rs">{d.ctxName}</div>}<div className="rk-bar"><i style={{ width: (d.total / data[0].total) * 100 + "%", background: d.color }} /></div></div>
                {keys.length > 1 && <Spark vals={d.vals} color={d.color} />}
                <div className="rk-r"><b className="tnum">R$ {fmt(d.total)}</b><span>{Math.round((d.total / total) * 100)}%{keys.length > 1 && <> · <Delta d={d.delta} kind={kind} /></>}</span></div>
                <GI n="chevD" s={14} style={{ color: "var(--l3)", transform: open === d.key ? "rotate(180deg)" : "none" }} />
              </button>
              {open === d.key && (
                <div className="tg-x">
                  <div className="tg-avg">Média <b className="tnum">R$ {fmt(d.avg)}</b>/mês · ativo em {d.active} de {keys.length} {keys.length === 1 ? "mês" : "meses"}</div>
                  <MonthCols g={d} keys={keys} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {view === "lines" && data.length > 0 && (() => {
        const shown = data.slice(0, 6).filter((d) => !hidden[d.key]);
        return (
          <div className="rcard">
            <Lines series={shown} keys={keys} />
            <div className="chips" style={{ marginTop: 10 }}>
              {data.slice(0, 6).map((d) => <button key={d.key} className={"chip" + (hidden[d.key] ? " off" : "")} onClick={() => setHidden({ ...hidden, [d.key]: !hidden[d.key] })}><span className="dot" style={{ background: hidden[d.key] ? "var(--l3)" : d.color }} />{d.name}</button>)}
            </div>
          </div>
        );
      })()}
      {view === "heat" && data.length > 0 && (
        <div className="rcard heat">
          <div className="hm-row hm-h"><span className="hm-n" />{keys.map((k) => <span key={k} className="hm-c">{monthShort(k)}</span>)}<span className="hm-t">Total</span></div>
          {data.map((d) => (
            <div key={d.key} className="hm-row">
              <span className="hm-n"><span className="dot" style={{ background: d.color }} />{d.name}</span>
              {d.vals.map((v, i) => <span key={i} className="hm-c tnum" style={{ background: v ? "color-mix(in srgb," + d.color + " " + Math.round((0.16 + 0.84 * v / mxCell) * 100) + "%, transparent)" : "var(--fill)", color: v / mxCell > .55 ? "#fff" : "var(--label)" }}>{v ? fmtK(v) : ""}</span>)}
              <span className="hm-t tnum">{fmtK(d.total)}</span>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

function ReportScreen({ pc, onBack }) {
  const [mode, setMode] = React.useState(() => localStorage.getItem("pc-ios-repmode") || "overview");
  const [period, setPeriod] = React.useState("mes");
  const [kind, setKind] = React.useState("expense");
  const [anchor, setAnchor] = React.useState(PC_CUR);
  const [menu, setMenu] = React.useState(false);
  const keys = periodKeys(period, anchor);
  const setM = (v) => { setMode(v); localStorage.setItem("pc-ios-repmode", v); };
  return (
    <>
      <Screen title="Relatório" left={<BackBtn onClick={onBack} />}
        right={<div className="gcap glass txt"><button onClick={() => setMenu(true)}>{kind === "income" ? "Receitas" : "Despesas"}<GI n="chevUD" s={14} /></button></div>}>
        <div className="pad rep-ctl">
          <Seg value={mode} onChange={setM} options={[["overview", "Visão geral"], ["tags", "Por tag"]]} />
          <Seg sm value={period} onChange={setPeriod} options={[["mes", "Mês"], ["tri", "Trimestre"], ["ano", "Ano"]]} />
        </div>
        {period === "mes" ? <MonthPill month={anchor} setMonth={setAnchor} /> : <div className="rep-range">{rangeLabel(keys)}</div>}
        {mode === "overview" ? <OverviewReport pc={pc} keys={keys} kind={kind} /> : <TagReport key={kind} pc={pc} keys={keys} kind={kind} />}
      </Screen>
      <GMenu open={menu} onClose={() => setMenu(false)} items={[
        { label: "Despesas", on: kind === "expense", icon: "arrUR", onClick: () => setKind("expense") },
        { label: "Receitas", on: kind === "income", icon: "arrDL", onClick: () => setKind("income") },
      ]} />
    </>
  );
}
Object.assign(window, { ReportScreen });
