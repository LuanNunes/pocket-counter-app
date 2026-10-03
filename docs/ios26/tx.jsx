// Transações — Despesas/Receitas, agrupar (Lista · Por categoria · Por tag), reordenar no modo Editar.
function groupTx(items, mode, kind, contexts) {
  const map = new Map();
  const put = (key, name, color, t, ord) => { if (!map.has(key)) map.set(key, { key, name, color, ord, items: [], total: 0 }); const g = map.get(key); g.items.push(t); g.total += Math.abs(t.amount); };
  for (const t of items) {
    if (mode === "none") { put(t.date, dayLabel(t.date), null, t, -Number(t.date.replace(/-/g, ""))); continue; }
    const tag = tagOf((t.tagIds || [])[0]);
    if (mode === "cat") {
      if (kind === "income") put("in:" + t.name, t.name, null, t, 0);
      else { const c = ctxOf(tag); c ? put(c.id, c.name, c.color, t, contexts.indexOf(c)) : put("_none", "Sem categoria", NEUTRAL, t, 999); }
    } else {
      if (tag) { const c = ctxOf(tag); put(tag.id, tag.name, tagColor(tag.id), t, (c ? contexts.indexOf(c) : 50) * 1000 + 0); }
      else put("_none", "Sem tag", NEUTRAL, t, 1e9);
    }
  }
  const gs = [...map.values()];
  if (mode === "cat" && kind === "income") { gs.sort((a, b) => b.total - a.total); const pal = ["oklch(0.6 0.14 155)", "oklch(0.55 0.14 250)", "oklch(0.64 0.14 70)", "oklch(0.58 0.15 320)", "oklch(0.6 0.12 200)"]; gs.forEach((g, i) => { g.color = pal[i % pal.length]; }); }
  else gs.sort((a, b) => a.ord - b.ord || a.name.localeCompare(b.name, "pt-BR"));
  return gs;
}

function TxRow({ t, pc, edit, onToggle, onOpen, drag, onGrip }) {
  const pend = t.status === "PENDING";
  const tag = tagOf((t.tagIds || [])[0]);
  const pay = payLabel(pc.cards, t);
  return (
    <div className={"txr" + (drag ? " dragging" : "")} style={drag ? { transform: "translateY(" + drag + "px)" } : null}>
      <button className={"st " + (pend ? "pend" : "paid")} onClick={onToggle} aria-label={pend ? "Pendente — marcar como paga" : "Paga — marcar como pendente"}><GI n={pend ? "clock" : "check"} s={16} w={2.4} /></button>
      <button className="txr-b" onClick={onOpen}>
        <div className="nm">{t.name}{t.fixo && <GI n="pin" s={12} style={{ color: "var(--tint)", marginLeft: 4 }} />}</div>
        <div className="mt">
          {tag && <span className="mtag"><span className="dot" style={{ background: tagColor(tag.id) }} />{tag.name}</span>}
          {t.tagIds && t.tagIds.length > 1 && <span className="more">+{t.tagIds.length - 1}</span>}
          {pay && <span className="pay">{pay}</span>}
        </div>
      </button>
      <div className="txr-r">
        <div className={"amt tnum " + (isInc(t) ? "inc" : "")}>{isInc(t) ? "+" : "−"} R$ {fmt(Math.abs(t.amount))}</div>
        <div className={"sl " + (pend ? "wrn" : "inc")}>{pend ? "pendente" : "paga"}</div>
      </div>
      {edit && <span className="grip" onPointerDown={onGrip} aria-label="Arrastar para reordenar"><GI n="grip" s={20} /></span>}
    </div>
  );
}

function TxGroup({ g, pc, month, mode, edit, open, onCollapse, onToggle, onOpen }) {
  const [order, setOrder] = React.useState(null);
  const [drag, setDrag] = React.useState(null);
  const ids = order || g.items.map((t) => t.id);
  const by = Object.fromEntries(g.items.map((t) => [t.id, t]));
  const ROW = 64;
  const grip = (e, id) => {
    e.preventDefault();
    const el = e.currentTarget; el.setPointerCapture(e.pointerId);
    const y0 = e.clientY, k = window.__PC_SCALE || 1, start = ids.indexOf(id); let cur = ids.slice();
    const mv = (ev) => {
      const dy = (ev.clientY - y0) / k;
      const to = Math.max(0, Math.min(cur.length - 1, start + Math.round(dy / ROW)));
      const nx = cur.filter((x) => x !== id); nx.splice(to, 0, id); cur = nx;
      setOrder(nx); setDrag({ id, dy: dy - (to - start) * ROW });
    };
    const up = () => { el.removeEventListener("pointermove", mv); el.removeEventListener("pointerup", up); el.removeEventListener("pointercancel", up); pc.reorder(month, cur); setOrder(null); setDrag(null); };
    el.addEventListener("pointermove", mv); el.addEventListener("pointerup", up); el.addEventListener("pointercancel", up);
  };
  const grouped = mode !== "none";
  return (
    <div className="txg">
      <button className={"txg-h" + (grouped ? " col" : "")} onClick={grouped ? onCollapse : undefined}>
        {grouped && <GI n="chevD" s={14} style={{ transform: open ? "none" : "rotate(-90deg)", color: "var(--l2)" }} />}
        {g.color && <span className="dot" style={{ background: g.color }} />}
        <span className="txg-n">{g.name}</span>{grouped && <span className="txg-c">{g.items.length}</span>}
        <span className="txg-t tnum">R$ {fmt(g.total)}</span>
      </button>
      {open && (
        <div className="list">
          {ids.map((id) => by[id] && <TxRow key={id} t={by[id]} pc={pc} edit={edit} drag={drag && drag.id === id ? drag.dy || 0.01 : 0}
            onToggle={() => onToggle(by[id])} onOpen={() => onOpen(by[id])} onGrip={(e) => grip(e, id)} />)}
        </div>
      )}
    </div>
  );
}

function TxDetail({ pc, month, t, onClose, toast }) {
  const [arm, setArm] = React.useState(false);
  React.useEffect(() => setArm(false), [t && t.id]);
  const tags = t ? (t.tagIds || []).map(tagOf).filter(Boolean) : [];
  return (
    <GSheet open={!!t} onClose={onClose} title="Lançamento">
      {t && <>
        <div className="qa-done" style={{ paddingTop: 4 }}>
          <div className={"qa-damt tnum " + (isInc(t) ? "inc" : "")}>{isInc(t) ? "+" : "−"} R$ {fmt(Math.abs(t.amount))}</div>
          <div className="qa-dnm">{t.name}</div>
        </div>
        <div className="list">
          <div className="row"><span className="rt rk">Data</span><span className="rv">{dayLabel(t.date)}</span></div>
          <div className="row"><span className="rt rk">Forma de Pagamento</span><span className="rv">{payLabel(pc.cards, t) || "—"}</span></div>
          <div className="row"><span className="rt rk">Categoria</span><span className="rv">{tags.length ? tags.map((x) => x.name).join(", ") : "—"}</span></div>
          <div className="row"><span className="rt rk">Paga</span><Sw on={t.status !== "PENDING"} onChange={(v) => pc.updateTx(month, t.id, { status: v ? "PAID" : "PENDING" })} /></div>
          <div className="row"><span className="rt"><div className="rk">Repete todo mês</div><div className="rs">Vira conta fixa</div></span><Sw on={!!t.fixo} onChange={(v) => pc.updateTx(month, t.id, { fixo: v })} /></div>
        </div>
        <div className="pad" style={{ marginTop: 14, display: "flex" }}>
          <button className="btn dst" onClick={() => { if (!arm) return setArm(true); pc.removeTx(month, t.id); toast("Lançamento excluído"); onClose(); }}><GI n="trash" s={18} />{arm ? "Confirmar exclusão" : "Excluir lançamento"}</button>
        </div>
      </>}
    </GSheet>
  );
}

function TxScreen({ pc, month, setMonth, onQA, toast }) {
  const [kind, setKind] = React.useState("expense");
  const [mode, setMode] = React.useState("none");
  const [edit, setEdit] = React.useState(false);
  const [menu, setMenu] = React.useState(false);
  const [onlyFixo, setOnlyFixo] = React.useState(false);
  const [q, setQ] = React.useState(null);
  const [closed, setClosed] = React.useState({});
  const [detail, setDetail] = React.useState(null);
  const all = pc.ledger[month] || [];
  const items = all.filter((t) => (kind === "income") === isInc(t)).filter((t) => !onlyFixo || t.fixo).filter((t) => !q || t.name.toLowerCase().includes(q.toLowerCase()));
  const groups = groupTx(items, mode, kind, pc.contexts);
  const total = items.reduce((s, t) => s + Math.abs(t.amount), 0);
  const toggle = (t) => { const nx = t.status === "PENDING" ? "PAID" : "PENDING"; pc.updateTx(month, t.id, { status: nx }); toast(nx === "PAID" ? "Marcada como paga" : "Marcada como pendente"); };
  const MODES = { none: "Lista", cat: "Por categoria", tag: "Por tag" };
  const detailTx = detail && all.find((t) => t.id === detail);
  return (
    <>
      <Screen title="Transações"
        left={edit ? <div className="gcap glass txt tint"><button onClick={() => setEdit(false)}>OK</button></div> : null}
        right={<>
          <div className="gcap glass">
            <button onClick={() => setQ(q == null ? "" : null)} aria-label="Buscar"><GI n="search" s={19} /></button>
            <button onClick={() => setMenu(true)} aria-label="Mais opções"><GI n="more" s={20} /></button>
          </div>
          <GBtn n="plus" tinted onClick={onQA} label="Novo lançamento" />
        </>}>
        <MonthPill month={month} setMonth={setMonth} />
        <div className="pad"><Seg value={kind} onChange={setKind} options={[["expense", "Despesas"], ["income", "Receitas"]]} /></div>
        {q != null && <div className="pad" style={{ marginTop: 10 }}><div className="sfield"><GI n="search" s={17} /><input autoFocus placeholder="Buscar" value={q} onChange={(e) => setQ(e.target.value)} /></div></div>}
        <div className="txsum">
          <div><div className={"txsum-v tnum " + (kind === "income" ? "inc" : "")}>R$ {fmt(total)}</div><div className="txsum-s">{items.length} {kind === "income" ? "receitas" : "despesas"}{onlyFixo ? " · só fixos" : ""}</div></div>
          <button className="chip" onClick={() => setMenu(true)}>{MODES[mode]}<GI n="chevUD" s={13} /></button>
        </div>
        {edit && <div className="edit-hint"><GI n="grip" s={15} />Arraste para reordenar{mode !== "none" ? " dentro do grupo" : " dentro do dia"}</div>}
        {groups.length === 0 && <div className="empty">Nenhuma {kind === "income" ? "receita" : "despesa"} neste mês.</div>}
        {groups.map((g) => <TxGroup key={mode + g.key} g={g} pc={pc} month={month} mode={mode} edit={edit} open={!closed[g.key]}
          onCollapse={() => setClosed((c) => ({ ...c, [g.key]: !c[g.key] }))} onToggle={toggle} onOpen={(t) => !edit && setDetail(t.id)} />)}
        {mode !== "none" && groups.length > 0 && <div className="txfoot"><span>Total</span><b className="tnum">R$ {fmt(total)}</b></div>}
      </Screen>
      <GMenu open={menu} onClose={() => setMenu(false)} items={[
        { head: "Agrupar" },
        { label: "Lista", on: mode === "none", icon: "list", onClick: () => setMode("none") },
        { label: "Por categoria", on: mode === "cat", icon: "pie", onClick: () => setMode("cat") },
        { label: "Por tag", on: mode === "tag", icon: "tag", onClick: () => setMode("tag") },
        { sep: true },
        { label: "Só fixos", on: onlyFixo, icon: "pin", onClick: () => setOnlyFixo(!onlyFixo) },
        { label: "Reordenar", icon: "grip", onClick: () => setEdit(true) },
      ]} />
      <TxDetail pc={pc} month={month} t={detailTx} onClose={() => setDetail(null)} toast={toast} />
    </>
  );
}
Object.assign(window, { TxScreen, groupTx });
