// Início — resumo do mês (espelha o Home redesign do Android).
function MonthPill({ month, setMonth, months = PC_MONTHS }) {
  const i = months.indexOf(month);
  return (
    <div className="mpill">
      <button disabled={i <= 0} onClick={() => setMonth(months[i - 1])} aria-label="Mês anterior"><GI n="chevL" s={18} /></button>
      <span className="mp-l"><b>{monthName(month)}</b> {month.slice(0, 4)}{month === PC_CUR && <em>atual</em>}</span>
      <button disabled={i >= months.length - 1} onClick={() => setMonth(months[i + 1])} aria-label="Próximo mês"><GI n="chevR" s={18} /></button>
    </div>
  );
}

function HomeScreen({ pc, month, setMonth, onQA, onDictate, onNav, onReport, toast, showField }) {
  const list = pc.ledger[month] || [];
  const t = totals(list);
  const nExp = list.filter((x) => !isInc(x)).length;
  const pending = window.SAMPLE_NOTIFICATIONS.filter((n) => n.status === "needs-review" || n.status === "needs-tags").length;
  const cardsTotal = pc.cards.reduce((s, c) => s + window.cardTotal(c), 0);
  return (
    <Screen title="Olá, Guilherme" right={<div className="av" aria-label="Perfil">G</div>}>
      {showField && <QaHomeField onOpen={onQA} onMic={onDictate} />}
      <MonthPill month={month} setMonth={setMonth} />
      <div className="hero">
        <div className="hero-k">Saldo do mês</div>
        <div className="hero-v tnum">{t.bal < 0 ? "−" : ""}R$ {fmt(Math.abs(t.bal))}</div>
        <div className="hero-kpis">
          <div><span className="kd exp" />Despesas<b className="tnum">R$ {fmt(t.exp)}</b><small>{nExp} lançs.</small></div>
          <div><span className="kd inc" />Receitas<b className="tnum">R$ {fmt(t.inc)}</b><small>{list.length - nExp} lançs.</small></div>
          {t.pendCount > 0 && <div><span className="kd wrn" />Pendente<b className="tnum">R$ {fmt(t.pend)}</b><small>{t.pendCount} em aberto</small></div>}
        </div>
      </div>
      {month === PC_CUR && pending > 0 && (
        <button className="list rev-banner row" onClick={() => toast("Abre o fluxo de ensino (igual ao Android)")}>
          <span className="ri" style={{ background: "var(--orange)" }}><GI n="warn" s={17} /></span>
          <span className="rt rk"><b className="tnum">{pending}</b> lançamentos para revisar</span>
          <span className="lnk">Ensinar</span><GI n="chevR" s={16} style={{ color: "var(--l3)" }} />
        </button>
      )}
      <div className="tiles">
        <button className="tile" onClick={onReport}>
          <span className="ri" style={{ background: "var(--tint)" }}><GI n="chart" s={18} /></span>
          <span className="tl-k">Relatório</span><span className="tl-v">Para onde foi</span>
        </button>
        <button className="tile" onClick={() => onNav("cartoes")}>
          <span className="ri" style={{ background: "#3a3a3c" }}><GI n="card" s={18} /></span>
          <span className="tl-k">Faturas · {pc.cards.length} cartões</span><span className="tl-v tnum">R$ {fmt(cardsTotal)}</span>
        </button>
      </div>
      <button className="list row lanc-cue" onClick={() => onNav("transacoes")}>
        <span className="rt"><div className="rk" style={{ fontWeight: 600 }}>Lançamentos</div><div className="rs">{list.length} no mês</div></span>
        <GI n="chevR" s={17} style={{ color: "var(--l3)" }} />
      </button>
    </Screen>
  );
}
Object.assign(window, { HomeScreen, MonthPill });
