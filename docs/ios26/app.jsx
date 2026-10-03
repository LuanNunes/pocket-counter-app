const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "dark": false,
  "phrase": "almoço 68 no cartão ontem"
}/*EDITMODE-END*/;

function App() {
  const [tw, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const pc = usePC();
  const init = new URLSearchParams(location.search).get("view");
  const [view, setView] = React.useState(init === "app" ? "app" : "lock");
  const [tab, setTab] = React.useState("inicio");
  const [sub, setSub] = React.useState(null);
  const [month, setMonth] = React.useState(PC_CUR);
  const [qa, setQa] = React.useState({ open: false, initial: null });
  const [siri, setSiri] = React.useState(null);
  const [action, setAction] = React.useState(null);
  const [live, setLive] = React.useState(null);
  const [opts, setOpts] = React.useState({ live: true, field: true });
  const [toast, setToast] = React.useState(null);
  const flash = (m) => { setToast(m); clearTimeout(window.__pcT); window.__pcT = setTimeout(() => setToast(null), 1900); };
  React.useEffect(() => { document.body.classList.toggle("dk", !!tw.dark); }, [tw.dark]);

  const openApp = () => { setView("app"); };
  const openQA = (initial) => { setView("app"); setQa({ open: true, initial: initial || null }); };
  const onVoiceDone = (last) => { if (opts.live) setLive(last); };
  const nav = (id) => { setSub(null); setTab(id); };
  const startSiri = () => { setAction(null); setSiri({ phrase: tw.phrase, k: Date.now() }); };
  const startAction = () => { setSiri(null); setAction({ phrase: tw.phrase, k: Date.now() }); };
  const goSub = (id) => { if (["fixas", "regras"].includes(id)) return flash("Igual ao Android — fora deste escopo"); setSub(id); };

  let body;
  if (sub === "report") body = <ReportScreen pc={pc} onBack={() => setSub(null)} />;
  else if (sub === "tags") body = <CategoriasScreen pc={pc} onBack={() => setSub(null)} toast={flash} />;
  else if (sub === "atalhos") body = <AtalhosScreen onBack={() => setSub(null)} onSiri={startSiri} onAction={startAction} onLock={() => setView("lock")} opts={opts} setOpt={(k, v) => setOpts({ ...opts, [k]: v })} />;
  else if (tab === "inicio") body = <HomeScreen pc={pc} month={month} setMonth={setMonth} onQA={() => openQA()} onDictate={() => openQA({ dictate: true })} onNav={nav} onReport={() => setSub("report")} toast={flash} showField={opts.field} />;
  else if (tab === "transacoes") body = <TxScreen pc={pc} month={month} setMonth={setMonth} onQA={() => openQA()} toast={flash} />;
  else if (tab === "cartoes") body = <CardsScreen pc={pc} />;
  else body = <MaisScreen onOpen={goSub} dark={tw.dark} setDark={(v) => setTweak("dark", v)} />;

  const onLock = view === "lock";
  return (
    <div className="stage">
      <Phone dark={tw.dark} outside={<>
        <button className="hw act" onClick={startAction} aria-label="Action Button"><span className="hw-tip">Action Button</span></button>
        <button className="hw side" onClick={startSiri} aria-label="Botão lateral (Siri)"><span className="hw-tip">Siri</span></button>
      </>}>
        {onLock ? (
          <LockScreen pc={pc} live={live} onOpen={openApp} onWidget={() => openQA()} onDictate={() => openQA({ dictate: true })}
            onLiveUndo={() => { pc.removeTx(live.monthKey, live.id); setLive(null); flash("Lançamento desfeito"); }}
            onLiveEdit={(l) => openQA({ last: l })} />
        ) : (
          <>
            {body}
            {!sub && <TabBar active={tab} onNav={nav} />}
            <QaSheet open={qa.open} initial={qa.initial} pc={pc} onClose={() => setQa({ open: false, initial: null })} toast={flash} />
          </>
        )}
        <StatusBar light={onLock || (siri && tw.dark)} />
        {action ? <ActionIsland key={action.k} phrase={action.phrase} pc={pc} onDone={onVoiceDone} onEnd={() => setAction(null)} onEdit={(l) => openQA({ last: l })} toast={flash} /> : <div className="island" />}
        {siri && <SiriOverlay key={siri.k} phrase={siri.phrase} pc={pc} onDone={onVoiceDone} onClose={() => setSiri(null)} onEdit={(l) => { setSiri(null); openQA({ last: l }); }} toast={flash} />}
        {toast && <div className="toast glass">{toast}</div>}
        <div className={"hind" + (onLock ? " light" : "")} />
      </Phone>
      <TweaksPanel title="Tweaks · PocketCounter iOS">
        <TweakSection label="Aparência" />
        <TweakToggle label="Tema escuro" value={tw.dark} onChange={(v) => setTweak("dark", v)} />
        <TweakSection label="Frase falada (Siri / Action Button)" />
        <TweakSelect label="Frase" value={tw.phrase} options={QA_SAMPLES} onChange={(v) => setTweak("phrase", v)} />
        <TweakButton label="Falar com a Siri" onClick={startSiri} />
        <TweakButton label="Pressionar Action Button" onClick={startAction} />
        <TweakSection label="Ir para" />
        <TweakButton label="Tela Bloqueada" secondary onClick={() => { setSiri(null); setView("lock"); }} />
        <TweakButton label="Início" secondary onClick={() => { setView("app"); nav("inicio"); }} />
        <TweakButton label="Transações" secondary onClick={() => { setView("app"); nav("transacoes"); }} />
        <TweakButton label="Relatório" secondary onClick={() => { setView("app"); setSub("report"); }} />
        <TweakButton label="Categorias & Tags" secondary onClick={() => { setView("app"); setTab("mais"); setSub("tags"); }} />
        <TweakButton label="Config. lançamento rápido" secondary onClick={() => { setView("app"); setTab("mais"); setSub("atalhos"); }} />
      </TweaksPanel>
    </div>
  );
}
ReactDOM.createRoot(document.getElementById("root")).render(<App />);
