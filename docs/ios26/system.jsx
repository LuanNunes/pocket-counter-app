// System surfaces: Tela Bloqueada (widgets + Live Activity), Siri (App Intent), Action Button → Dynamic Island.
function useVoice(phrase, pc, onDone) {
  const [st, setSt] = React.useState({ stage: "listen", text: "" });
  React.useEffect(() => {
    let alive = true, i = 0, t;
    const tick = () => {
      if (!alive) return; i++; setSt({ stage: "listen", text: phrase.slice(0, i) });
      if (i < phrase.length) t = setTimeout(tick, 42);
      else t = setTimeout(() => {
        if (!alive) return;
        const p = window.NLQ.parse(phrase, qaCtx(pc));
        if (!p.ok) return setSt({ stage: "fail", text: phrase });
        if (p.ask) setSt({ stage: "ask", text: phrase, draft: p });
        else { const last = qaCommit(pc, p); setSt({ stage: "done", text: phrase, last }); onDone && onDone(last); }
      }, 550);
    };
    t = setTimeout(tick, 650);
    return () => { alive = false; clearTimeout(t); };
  }, [phrase]);
  const finish = (d) => { const last = qaCommit(pc, d); setSt((s) => ({ stage: "done", text: s.text, last })); onDone && onDone(last); };
  const answer = (v) => { const d = qaAnswer(pc, st.draft, v); if (!d) return; if (d.ask) setSt({ ...st, draft: d }); else finish(d); };
  const skip = () => finish({ ...st.draft, ask: null });
  return [st, answer, skip];
}
const AppIcon = ({ s = 30 }) => <span className="isl-app" style={{ width: s, height: s, borderRadius: s * .28 }}><GI n="spark" s={s * .55} style={{ color: "#fff" }} /></span>;
const signed = (t) => (t.amount > 0 ? "+" : "−") + " R$ " + fmt(Math.abs(t.amount));

function FieldPills({ pc, last, dark }) {
  return (
    <div className={"fpills" + (dark ? " dk" : "")}>
      {qaFieldInfo(pc, last).map((f) => (
        <div key={f.key} className="fp"><span className="fp-k">{f.label}</span><span className={"fp-v" + (f.weak ? " weak" : "")}>{f.value}</span><QaBadge b={f.badge} /></div>
      ))}
    </div>
  );
}

function SiriOverlay({ phrase, pc, onClose, onEdit, onDone, toast }) {
  const [st, answer, skip] = useVoice(phrase, pc, onDone);
  const undo = () => { pc.removeTx(st.last.monthKey, st.last.id); toast("Lançamento desfeito"); onClose(); };
  return (
    <div className="siri" onClick={(e) => { if (e.target === e.currentTarget && st.stage !== "listen") onClose(); }}>
      <div className={"siri-glow" + (st.stage === "listen" ? " live" : "")} />
      <div className="siri-panel">
        {st.stage === "listen" && <div className="siri-q glass"><span className="siri-orb" /><span>{st.text || "…"}</span></div>}
        {st.stage !== "listen" && <div className="siri-say">{st.stage === "ask" ? st.draft.ask.question : st.stage === "fail" ? "Não entendi o valor. Pode repetir?" : "Pronto, lancei " + signed(st.last).replace("− ", "") + " em " + st.last.name + "."}</div>}
        {(st.stage === "ask" || st.stage === "done") && (
          <div className="snip glass">
            <div className="snip-hd"><AppIcon s={26} /><b>PocketCounter</b><span>{st.stage === "done" ? "Lançado agora" : "Lançamento rápido"}</span></div>
            {st.stage === "ask" && <>
              <div className="snip-q">“{st.text}”</div>
              {st.draft.ask.options ? (
                <div className="snip-opts">{st.draft.ask.options.map((o) => <button key={String(o.value)} className="snip-btn" onClick={() => answer(o.value)}>{o.label}</button>)}{st.draft.ask.allowSkip && <button className="snip-btn ghost" onClick={skip}>Sem cartão específico</button>}</div>
              ) : <div className="snip-opts">{["10", "50", "100"].map((v) => <button key={v} className="snip-btn" onClick={() => answer(v)}>R$ {v}</button>)}</div>}
            </>}
            {st.stage === "done" && <>
              <div className="snip-res"><span className="qa-orb sm"><GI n="check" s={18} /></span><div><div className="snip-amt tnum">{signed(st.last)}</div><div className="snip-nm">{st.last.name} · pendente</div></div></div>
              <FieldPills pc={pc} last={st.last} />
              <div className="snip-acts"><button className="snip-btn" onClick={undo}><GI n="undo" s={16} />Desfazer</button><button className="snip-btn pri" onClick={() => onEdit(st.last)}>Alterar</button></div>
            </>}
          </div>
        )}
      </div>
    </div>
  );
}

function ActionIsland({ phrase, pc, onEnd, onEdit, onDone, toast }) {
  const [st, answer, skip] = useVoice(phrase, pc, onDone);
  const [mode, setMode] = React.useState("expanded");
  React.useEffect(() => {
    if (st.stage !== "done") return;
    const a = setTimeout(() => setMode("compact"), 4200), b = setTimeout(onEnd, 9000);
    return () => { clearTimeout(a); clearTimeout(b); };
  }, [st.stage]);
  if (mode === "compact" && st.last) return (
    <button className="island compact" onClick={() => setMode("expanded")}>
      <div className="isl-c"><span className="ic ok"><GI n="check" s={14} /></span><span className="tnum">{signed(st.last)}</span></div>
    </button>
  );
  return (
    <div className="island expanded">
      <div className="isl-x">
        <div className="isl-hd"><AppIcon /><div style={{ flex: 1 }}><div className="isl-k">Lançamento rápido</div>{st.stage === "listen" && <div className="isl-t">Ouvindo…</div>}{st.stage === "ask" && <div className="isl-t">{st.draft.ask.question}</div>}{st.stage === "done" && <div className="isl-t">Lançado</div>}</div>
          {st.stage === "listen" && <span className="wave"><i /><i /><i /><i /><i /></span>}
          {st.stage !== "listen" && <button className="isl-close" onClick={onEnd} aria-label="Fechar"><GI n="x" s={14} /></button>}
        </div>
        {st.stage === "listen" && <div className="isl-txt">{st.text}<span className="cur" /></div>}
        {st.stage === "ask" && <>
          <div className="isl-meta" style={{ fontSize: 15 }}>“{st.text}”</div>
          <div className="isl-opts">{(st.draft.ask.options || []).map((o) => <button key={String(o.value)} className="isl-btn" style={{ flex: "1 1 40%" }} onClick={() => answer(o.value)}>{o.label}</button>)}{st.draft.ask.allowSkip && <button className="isl-btn" style={{ flex: "1 1 100%", background: "transparent", color: "rgba(255,255,255,.7)" }} onClick={skip}>Sem cartão específico</button>}</div>
        </>}
        {st.stage === "fail" && <div className="isl-meta">Não identifiquei um valor. Tente de novo.</div>}
        {st.stage === "done" && <>
          <div className="isl-res"><div style={{ flex: 1 }}><div className="isl-amt tnum">{signed(st.last)}</div><div className="isl-meta">{st.last.name} · {st.last.type === "income" ? "receita" : "despesa"} pendente</div></div></div>
          <FieldPills pc={pc} last={st.last} dark />
          <div className="isl-acts"><button className="isl-btn" onClick={() => { pc.removeTx(st.last.monthKey, st.last.id); toast("Lançamento desfeito"); onEnd(); }}><GI n="undo" s={15} />Desfazer</button><button className="isl-btn pri" onClick={() => { onEdit(st.last); onEnd(); }}>Alterar</button></div>
        </>}
      </div>
    </div>
  );
}

function LockScreen({ pc, live, onOpen, onWidget, onDictate, onLiveUndo, onLiveEdit }) {
  const t = totals(pc.ledger[PC_CUR] || []);
  const notifs = window.SAMPLE_NOTIFICATIONS.slice(0, 2);
  return (
    <div className="lock">
      <div className="lk-top">
        <div className="lk-date">terça-feira, 19 de maio</div>
        <div className="lk-clock">9:41</div>
        <div className="lk-widgets">
          <button className="wg wg-rect" onClick={onWidget}>
            <span className="wg-h"><GI n="spark" s={13} />PocketCounter</span>
            <span className="wg-v tnum">R$ {fmt(t.exp)}</span>
            <span className="wg-s">gasto em maio · lançar</span>
          </button>
          <button className="wg wg-circ" onClick={onWidget} aria-label="Lançar"><GI n="plus" s={26} /><span>Lançar</span></button>
          <button className="wg wg-circ" onClick={onDictate} aria-label="Ditar lançamento"><GI n="mic" s={24} /><span>Ditar</span></button>
        </div>
      </div>
      <div className="lk-feed">
        {live && (
          <div className="la">
            <div className="la-hd"><AppIcon s={24} /><b>PocketCounter</b><span>agora</span></div>
            <div className="la-body">
              <div><div className="la-amt tnum">{signed(live)}</div><div className="la-nm">{live.name} · lançado</div></div>
              <div className="la-acts"><button onClick={onLiveUndo} aria-label="Desfazer"><GI n="undo" s={18} /></button><button className="pri" onClick={() => onLiveEdit(live)}>Alterar</button></div>
            </div>
            <FieldPills pc={pc} last={live} dark />
          </div>
        )}
        {notifs.map((n) => (
          <button key={n.id} className="lk-n" onClick={onOpen}>
            <AppIcon s={36} />
            <div className="lk-nb"><div className="lk-nh"><b>PocketCounter</b><span>{n.time}</span></div><div className="lk-ns">{n.app}</div><div className="lk-nt">{n.text}</div>
              <span className={"lk-tag" + (n.status === "auto" ? "" : " rev")}>{n.status === "auto" ? "Classificado" : "Toque para classificar"}</span></div>
          </button>
        ))}
      </div>
      <div className="lk-bot">
        <button className="lk-circ" aria-label="Lanterna"><GI n="flash" s={22} /></button>
        <button className="lk-open" onClick={onOpen}>Deslize para cima para abrir</button>
        <button className="lk-circ" aria-label="Câmera"><GI n="camera" s={22} /></button>
      </div>
    </div>
  );
}
Object.assign(window, { SiriOverlay, ActionIsland, LockScreen, AppIcon });
