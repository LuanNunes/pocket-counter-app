// Lançamento rápido — linguagem natural (mesmo contrato do nl-parse.js do Android).
const QA_SAMPLES = ["paguei 250 em uma consulta do cachorro", "pix de 10 pra minha esposa", "recebi 125 de dividendos", "almoço 68 no cartão ontem", "paguei 10 de estacionamento"];

function qaCtx(pc) { return { cards: pc.cards, tags: pc.tags, contexts: pc.contexts, today: PC_TODAY }; }
function qaCommit(pc, dr) {
  const mk = (dr.date || PC_TODAY).slice(0, 7);
  const srcOf = (k) => { const f = (dr.fields || []).find((x) => x.key === k); return f ? f.from : null; };
  const tx = { date: dr.date, name: dr.name, paymentMethod: dr.method || null, cardId: dr.cardId || null, amount: dr.type === "expense" ? -dr.amount : dr.amount, type: dr.type, tagIds: (dr.tagIds || []).slice(), status: "PENDING", fixo: false };
  const id = pc.addTx(mk, tx);
  return { ...tx, id, monthKey: mk, suggest: dr.suggest, src: { date: srcOf("date") === "frase", method: !!dr.method, tags: (dr.tagIds || []).length > 0 }, edited: {} };
}
function qaPatch(pc, last, patch) {
  pc.updateTx(last.monthKey, last.id, patch);
  const ed = {};
  if ("date" in patch) ed.date = true;
  if ("paymentMethod" in patch) ed.method = true;
  if ("tagIds" in patch) ed.tags = true;
  return { ...last, ...patch, src: { date: ed.date ? false : last.src.date, method: ed.method ? false : last.src.method, tags: ed.tags ? false : last.src.tags }, edited: { ...last.edited, ...ed }, suggest: patch.tagIds ? null : last.suggest };
}
// resolve a pending "ask" answer → updated draft (or a follow-up ask)
function qaAnswer(pc, d, value) {
  d = { ...d }; const k = d.ask.key;
  if (k === "amount") { const v = parseFloat(String(value).replace(/\./g, "").replace(",", ".")); if (!isFinite(v) || v <= 0) return null; d.amount = v; }
  else if (k === "cardId") { d.cardId = value; d.method = "credit"; }
  else if (k === "type") { d.type = value; const re = window.NLQ.parse(d.raw + (value === "income" ? " recebi" : " paguei"), qaCtx(pc)); d.tagIds = re.tagIds; d.suggest = re.suggest; }
  if (k !== "cardId" && d.method === "credit" && !d.cardId && pc.cards.length > 1) {
    d.ask = { key: "cardId", question: "Em qual cartão?", options: pc.cards.map((c) => ({ label: c.name, value: c.id })), allowSkip: true };
    return d;
  }
  d.ask = null; return d;
}
const shiftDay = (n) => { const b = new Date(PC_TODAY + "T12:00:00"); b.setDate(b.getDate() + n); return b.getFullYear() + "-" + String(b.getMonth() + 1).padStart(2, "0") + "-" + String(b.getDate()).padStart(2, "0"); };

function qaFieldInfo(pc, last) {
  const cardName = (id) => { const c = pc.cards.find((x) => x.id === id); return c ? c.name : null; };
  const tagNames = (last.tagIds || []).map((id) => (tagOf(id) || {}).name).filter(Boolean);
  return [
    { key: "date", label: "Data", value: ddmm(last.date) + (last.date === PC_TODAY ? " · hoje" : last.date === shiftDay(-1) ? " · ontem" : ""),
      badge: last.src.date ? "frase" : last.edited.date ? "definido" : "assumido", weak: !last.src.date && !last.edited.date },
    { key: "method", label: "Forma de Pagamento", value: last.paymentMethod ? (last.cardId ? cardName(last.cardId) : window.NLQ.METHOD_LABEL[last.paymentMethod]) : "não informado",
      badge: last.src.method ? "frase" : last.edited.method ? "definido" : null, weak: !last.paymentMethod },
    { key: "tag", label: "Categoria", value: tagNames.length ? tagNames.join(", ") : "sem categoria", dot: tagNames.length ? tagColor(last.tagIds[0]) : null,
      badge: last.src.tags ? "frase" : last.edited.tags ? "definido" : null, weak: !tagNames.length,
      hint: last.suggest && last.suggest.contextName ? "parece " + last.suggest.contextName : null },
  ];
}
const BADGE_TXT = { frase: "da frase", assumido: "assumido", definido: "definido" };
const QaBadge = ({ b }) => b ? <span className={"badge " + b}>{BADGE_TXT[b]}</span> : null;

function QaReview({ pc, last, onLast }) {
  const [ed, setEd] = React.useState(null);
  const f = qaFieldInfo(pc, last);
  const patch = (p) => { onLast(qaPatch(pc, last, p)); setEd(null); };
  const tagOpts = ((last.suggest && last.suggest.options) || []).length ? last.suggest.options : pc.tags.filter((t) => (t.kind || "expense") === last.type).slice(0, 9);
  const opts = {
    date: [["Hoje", shiftDay(0)], ["Ontem", shiftDay(-1)], ["Anteontem", shiftDay(-2)]].map(([lb, v]) => <button key={lb} className={"chip" + (last.date === v ? " on" : "")} onClick={() => patch({ date: v })}>{lb}</button>),
    method: [...[["pix", "Pix"], ["debit", "Débito"], ["cash", "Dinheiro"]].map(([v, lb]) => <button key={v} className={"chip" + (last.paymentMethod === v ? " on" : "")} onClick={() => patch({ paymentMethod: v, cardId: null })}>{lb}</button>),
      ...pc.cards.map((c) => <button key={c.id} className={"chip" + (last.cardId === c.id ? " on" : "")} onClick={() => patch({ paymentMethod: "credit", cardId: c.id })}><GI n="card" s={14} />{c.name.replace(/^Cartão /, "")}</button>)],
    tag: tagOpts.map((t) => <button key={t.id} className={"chip" + ((last.tagIds || []).includes(t.id) ? " on" : "")} onClick={() => patch({ tagIds: [...(last.tagIds || []).filter((x) => x !== t.id), t.id] })}><span className="dot" style={{ background: tagColor(t.id) }} />{t.name}</button>),
  };
  return (
    <div className="list qa-rev">
      {f.map((r) => (
        <div key={r.key} className={"qa-rr" + (ed === r.key ? " open" : "")}>
          <button className="row" onClick={() => setEd(ed === r.key ? null : r.key)}>
            <div className="rt">
              <div className="qa-rk">{r.label}</div>
              <div className="qa-rv">
                {r.dot && <span className="dot" style={{ background: r.dot }} />}
                <span className={r.weak ? "weak" : ""}>{r.value}</span>
                <QaBadge b={r.badge} />
              </div>
              {r.hint && <div className="qa-hint"><GI n="spark" s={12} />{r.hint}</div>}
            </div>
            <span className="qa-cta">{ed === r.key ? "fechar" : r.weak ? "definir" : "alterar"}</span>
          </button>
          {ed === r.key && <div className="qa-opts chips">{opts[r.key]}</div>}
        </div>
      ))}
    </div>
  );
}

function QaHomeField({ onOpen, onMic }) {
  return (
    <div className="qa-home">
      <button className="qa-home-b" onClick={onOpen}>
        <span className="qa-home-ic"><GI n="spark" s={16} /></span>
        <span className="qa-home-ph">O que você gastou ou recebeu?</span>
      </button>
      <button className="qa-home-mic" onClick={onMic || onOpen} aria-label="Ditar"><GI n="mic" s={19} /></button>
    </div>
  );
}

function QaSheet({ open, pc, onClose, toast, initial }) {
  const [text, setText] = React.useState("");
  const [stage, setStage] = React.useState("input");
  const [draft, setDraft] = React.useState(null);
  const [askVal, setAskVal] = React.useState("");
  const [last, setLast] = React.useState(null);
  const [dict, setDict] = React.useState(false);
  const ta = React.useRef(null);
  React.useEffect(() => {
    if (!open) return;
    if (initial && initial.last) { setLast(initial.last); setStage("done"); }
    else { setStage("input"); setLast(null); setText(""); setTimeout(() => ta.current && ta.current.focus(), 420); if (initial && initial.dictate) dictate(); }
  }, [open]);
  const commit = (d) => { setLast(qaCommit(pc, d)); setStage("done"); setText(""); setDraft(null); };
  const submit = (t) => { const p = window.NLQ.parse(t != null ? t : text, qaCtx(pc)); if (!p.ok) return; if (p.ask) { setDraft(p); setAskVal(""); setStage("ask"); return; } commit(p); };
  const answer = (v) => { const d = qaAnswer(pc, draft, v); if (!d) return; if (d.ask) setDraft(d); else commit(d); };
  const dictate = () => {
    const ph = QA_SAMPLES[Math.floor(Math.random() * QA_SAMPLES.length)]; setDict(true); setText(""); let i = 0;
    const tick = () => { i++; setText(ph.slice(0, i)); if (i < ph.length) setTimeout(tick, 38); else setDict(false); };
    setTimeout(tick, 300);
  };
  const undo = () => { pc.removeTx(last.monthKey, last.id); toast("Lançamento desfeito"); onClose(); };
  const canSend = text.trim().length > 1 && !dict;
  const d = draft;

  let footer = null;
  if (stage === "input") footer = <button className="btn pri" disabled={!canSend} onClick={() => submit()}><GI n="up" s={18} />Lançar</button>;
  else if (stage === "ask" && d && !d.ask.options) footer = <button className="btn pri" disabled={!askVal.trim()} onClick={() => answer(askVal)}>Confirmar</button>;
  else if (stage === "ask") footer = <button className="btn sec" onClick={() => { setStage("input"); setText(d.raw); }}>Editar a frase</button>;
  else if (stage === "done") footer = <><button className="btn sec" onClick={undo}><GI n="undo" s={17} />Desfazer</button><button className="btn pri" onClick={() => { setStage("input"); setLast(null); setTimeout(() => ta.current && ta.current.focus(), 60); }}>Lançar outro</button></>;

  return (
    <GSheet open={open} onClose={onClose} title="Lançamento rápido" footer={footer}
      right={stage === "done" ? <button className="gbtn tinted" onClick={onClose} aria-label="Concluir"><GI n="check" s={18} /></button> : null}>
      {stage === "input" && (
        <div className="pad">
          <div className={"qa-field" + (dict ? " dict" : "")}>
            <textarea ref={ta} rows={3} value={text} placeholder="Ex.: paguei 250 em uma consulta do cachorro" onChange={(e) => setText(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); if (canSend) submit(); } }} />
          </div>
          <div className="qa-voice">
            <button className={"qa-mic" + (dict ? " on" : "")} onClick={dictate} aria-label="Ditar">{dict ? <span className="wave"><i /><i /><i /><i /><i /></span> : <GI n="mic" s={24} />}</button>
            <div><b>{dict ? "Ouvindo…" : "Ditar"}</b><span>Ou diga “E aí Siri, lançar no PocketCounter”.</span></div>
          </div>
        </div>
      )}
      {stage === "ask" && d && (
        <div className="pad">
          <div className="qa-read">
            <span className="qa-read-k">Entendi</span>
            {d.amount != null && <span className={"chip " + (d.type === "income" ? "inc" : "")}>R$ {fmt(d.amount)}</span>}
            <span className="chip">{d.type === "income" ? "Receita" : "Despesa"}</span>
            <span className="chip">{d.name}</span>
            <span className="chip"><GI n="cal" s={13} />{ddmm(d.date)}</span>
            {d.method && <span className="chip"><GI n="card" s={13} />{window.NLQ.METHOD_LABEL[d.method]}</span>}
          </div>
          <div className="qa-q">{d.ask.question}</div>
          {d.ask.options ? (
            <div className="list">
              {d.ask.options.map((o) => <button key={String(o.value)} className="row" onClick={() => answer(o.value)}><span className="rt rk">{o.label}</span><GI n="chevR" s={16} style={{ color: "var(--l3)" }} /></button>)}
              {d.ask.allowSkip && <button className="row" onClick={() => commit({ ...d, ask: null })}><span className="rt rk" style={{ color: "var(--l2)" }}>Sem cartão específico</span></button>}
            </div>
          ) : (
            <div className="qa-amt"><span>R$</span><input autoFocus className="tnum" value={askVal} placeholder="0,00" inputMode="decimal" onChange={(e) => setAskVal(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") answer(askVal); }} /></div>
          )}
        </div>
      )}
      {stage === "done" && last && (
        <>
          <div className="qa-done">
            <span className="qa-orb"><GI n="check" s={26} /></span>
            <div className="qa-damt tnum">{last.amount > 0 ? "+" : "−"} R$ {fmt(Math.abs(last.amount))}</div>
            <div className="qa-dnm">{last.name}</div>
            <div className="qa-dmeta">{last.type === "income" ? "Receita" : "Despesa"} lançada · pendente</div>
          </div>
          <div className="sec-h" style={{ fontSize: 17, paddingTop: 6 }}>O que foi lançado</div>
          <QaReview pc={pc} last={last} onLast={setLast} />
          <div className="foot-note">Toque em um campo para corrigir. A alteração é salva no lançamento.</div>
        </>
      )}
    </GSheet>
  );
}

Object.assign(window, { QA_SAMPLES, qaCtx, qaCommit, qaPatch, qaAnswer, qaFieldInfo, QaBadge, QaReview, QaHomeField, QaSheet });
