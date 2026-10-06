// Mais · Categorias & Tags · Lançamento rápido (atalhos) · Cartões
const CTX_SWATCHES = ["#7b5bf5", "#0e9bb0", "#23a268", "#dd9627", "#e25555", "#e0529c", "#b558d6", "#5566ef", "#8a9b2a", "#e07b3c"];

function MaisScreen({ onOpen, dark, setDark }) {
  const R = ({ ic, bg, k, s, id }) => (
    <button className="row" onClick={() => onOpen(id)} style={{ "--in": "58px" }}>
      <span className="ri" style={{ background: bg }}><GI n={ic} s={17} /></span>
      <span className="rt"><div className="rk">{k}</div>{s && <div className="rs">{s}</div>}</span>
      <GI n="chevR" s={16} style={{ color: "var(--l3)" }} />
    </button>
  );
  return (
    <Screen title="Mais">
      <div className="list"><div className="row" style={{ padding: "14px 16px" }}><div className="av" style={{ width: 56, height: 56, borderRadius: 28, fontSize: 22 }}>G</div><div className="rt"><div style={{ fontSize: 21, fontWeight: 600 }}>Guilherme</div><div className="rs">guilherme@email.com</div></div></div></div>
      <div className="sec-h">Organização</div>
      <div className="list">
        <R id="tags" ic="tag" bg="oklch(0.6 0.16 30)" k="Categorias & Tags" s="Cores, tags por categoria" />
        <R id="report" ic="chart" bg="var(--tint)" k="Relatório" s="Visão geral e por tag" />
        <R id="fixas" ic="recur" bg="oklch(0.55 0.13 160)" k="Contas fixas" />
        <R id="regras" ic="book" bg="oklch(0.55 0.14 240)" k="Regras aprendidas" />
      </div>
      <div className="sec-h">Lançamento rápido</div>
      <div className="list">
        <R id="atalhos" ic="spark" bg="oklch(0.5 0.2 290)" k="Siri, Action Button e widgets" s="Lance sem abrir o app" />
      </div>
      <div className="sec-h">Aparência</div>
      <div className="list"><div className="row" style={{ "--in": "58px" }}><span className="ri" style={{ background: "#3a3a3c" }}><GI n="moon" s={17} /></span><span className="rt rk">Tema escuro</span><Sw on={dark} onChange={setDark} /></div></div>
    </Screen>
  );
}

function CategoriasScreen({ pc, onBack, toast }) {
  const [edit, setEdit] = React.useState(false);
  const [addCat, setAddCat] = React.useState(false);
  const [addTag, setAddTag] = React.useState(null); // ctx id | "income"
  const [del, setDel] = React.useState(null);
  const [name, setName] = React.useState("");
  const [color, setColor] = React.useState(CTX_SWATCHES[0]);
  const income = pc.tags.filter((t) => t.kind === "income");
  const openCat = () => { setName(""); setColor(CTX_SWATCHES[pc.contexts.length % CTX_SWATCHES.length]); setAddCat(true); };
  const openTag = (id) => { setName(""); setAddTag(id); };
  const delCtx = del && pc.contexts.find((c) => c.id === del);
  return (
    <>
      <Screen title="Categorias & Tags" sub="Despesas por categoria → tag" left={<BackBtn onClick={onBack} />}
        right={<><div className="gcap glass txt tint"><button onClick={() => setEdit(!edit)}>{edit ? "OK" : "Editar"}</button></div><GBtn n="plus" tinted onClick={openCat} label="Adicionar categoria" /></>}>
        <div className="cat-grid">
          {pc.contexts.map((c) => {
            const tags = pc.tags.filter((t) => t.idContext === c.id);
            return (
              <div key={c.id} className="catc">
                <div className="catc-h">
                  <span className="dot" style={{ background: c.color, width: 12, height: 12 }} />
                  <span className="catc-n">{c.name}</span><span className="catc-c">{tags.length}</span>
                  {edit && <button className="catc-del" onClick={() => setDel(c.id)} aria-label="Excluir categoria"><GI n="trash" s={17} /></button>}
                </div>
                <div className="chips">
                  {tags.map((t) => <span key={t.id} className="chip"><span className="dot" style={{ background: c.color }} />{t.name}{edit && <button className="chip-x" onClick={() => { pc.removeTag(t.id); toast("Tag excluída"); }} aria-label={"Excluir " + t.name}><GI n="x" s={11} /></button>}</span>)}
                  <button className="chip ghost" onClick={() => openTag(c.id)}><GI n="plus" s={13} />Tag</button>
                </div>
              </div>
            );
          })}
        </div>
        <div className="sec-h">Receitas<small>categorias planas</small></div>
        <div className="catc" style={{ margin: "0 16px" }}>
          <div className="chips">
            {income.map((t) => <span key={t.id} className="chip"><span className="dot" style={{ background: t.color }} />{t.name}{edit && <button className="chip-x" onClick={() => pc.removeTag(t.id)}><GI n="x" s={11} /></button>}</span>)}
            <button className="chip ghost" onClick={() => openTag("income")}><GI n="plus" s={13} />Tag</button>
          </div>
        </div>
      </Screen>
      <GSheet open={addCat} onClose={() => setAddCat(false)} title="Nova categoria"
        right={<button className="gbtn tinted" disabled={!name.trim()} style={{ opacity: name.trim() ? 1 : .4 }} onClick={() => { pc.addContext(name.trim(), color); setAddCat(false); toast("Categoria adicionada"); }} aria-label="Salvar"><GI n="check" s={18} /></button>}>
        <div className="list"><div className="row"><input className="tf" autoFocus placeholder="Nome da categoria" value={name} onChange={(e) => setName(e.target.value)} /></div></div>
        <div className="sec-h" style={{ fontSize: 17 }}>Cor</div>
        <div className="swatches">{CTX_SWATCHES.map((c) => <button key={c} className={"swatch" + (color === c ? " on" : "")} style={{ background: c }} onClick={() => setColor(c)} aria-label={c}>{color === c && <GI n="check" s={16} />}</button>)}</div>
        <div className="foot-note" style={{ paddingTop: 14 }}>A cor aparece nos grupos de Transações, no Relatório e nas tags desta categoria.</div>
      </GSheet>
      <GSheet open={!!addTag} onClose={() => setAddTag(null)} title="Nova tag"
        right={<button className="gbtn tinted" style={{ opacity: name.trim() ? 1 : .4 }} onClick={() => { if (!name.trim()) return; pc.addTag(name.trim(), addTag === "income" ? null : addTag, addTag === "income" ? "income" : null); setAddTag(null); toast("Tag adicionada"); }} aria-label="Salvar"><GI n="check" s={18} /></button>}>
        <div className="list"><div className="row"><input className="tf" autoFocus placeholder="Nome da tag" value={name} onChange={(e) => setName(e.target.value)} /></div></div>
        {addTag !== "income" && <>
          <div className="sec-h" style={{ fontSize: 17 }}>Categoria</div>
          <div className="pad chips">{pc.contexts.map((c) => <button key={c.id} className={"chip" + (addTag === c.id ? " on" : "")} onClick={() => setAddTag(c.id)}><span className="dot" style={{ background: addTag === c.id ? "#fff" : c.color }} />{c.name}</button>)}</div>
        </>}
      </GSheet>
      <GAlert open={!!delCtx} title={delCtx ? "Excluir " + delCtx.name + "?" : ""} msg="As tags desta categoria também serão excluídas."
        actions={[{ label: "Cancelar", onClick: () => setDel(null) }, { label: "Excluir", dst: true, onClick: () => { pc.removeContext(del); setDel(null); toast("Categoria e suas tags excluídas"); } }]} />
    </>
  );
}

function AtalhosScreen({ onBack, onSiri, onAction, onLock, opts, setOpt }) {
  return (
    <Screen title="Lançamento rápido" sub="Registre um gasto sem abrir o app." left={<BackBtn onClick={onBack} />}>
      <div className="sec-h">Siri e Atalhos</div>
      <div className="list">
        <div className="row" style={{ "--in": "58px" }}><span className="ri" style={{ background: "linear-gradient(135deg,#ff6ac1,#7b5bf5,#3fb6ff)" }}><GI n="mic" s={17} /></span><span className="rt"><div className="rk">“Lançar no PocketCounter”</div><div className="rs">Depois diga o gasto. Ex.: almoço 68 no cartão ontem</div></span></div>
        <button className="row" onClick={onSiri}><span className="rt rk" style={{ color: "var(--tint)", paddingLeft: 42 }}>Testar com a Siri</span></button>
      </div>
      <div className="sec-h">Action Button</div>
      <div className="list">
        <div className="row" style={{ "--in": "58px" }}><span className="ri" style={{ background: "#ff9500" }}><GI n="action" s={17} /></span><span className="rt"><div className="rk">Atalho: Lançamento rápido</div><div className="rs">Segure o botão e fale. O resultado aparece na Dynamic Island.</div></span></div>
        <button className="row" onClick={onAction}><span className="rt rk" style={{ color: "var(--tint)", paddingLeft: 42 }}>Simular Action Button</span></button>
      </div>
      <div className="sec-h">Tela Bloqueada</div>
      <div className="list">
        <div className="row" style={{ "--in": "58px" }}><span className="ri" style={{ background: "#5856d6" }}><GI n="widget" s={17} /></span><span className="rt"><div className="rk">Widget “Lançar”</div><div className="rs">Abre direto no campo de lançamento</div></span></div>
        <div className="row" style={{ "--in": "58px" }}><span className="ri" style={{ background: "#000" }}><GI n="island" s={17} /></span><span className="rt"><div className="rk">Live Activity do último lançamento</div><div className="rs">Fica 1 h na Tela Bloqueada com Desfazer e Alterar</div></span><Sw on={opts.live} onChange={(v) => setOpt("live", v)} /></div>
        <button className="row" onClick={onLock}><span className="rt rk" style={{ color: "var(--tint)", paddingLeft: 42 }}>Ver na Tela Bloqueada</span></button>
      </div>
      <div className="sec-h">No app</div>
      <div className="list"><div className="row" style={{ "--in": "58px" }}><span className="ri" style={{ background: "var(--tint)" }}><GI n="spark" s={17} /></span><span className="rt"><div className="rk">Campo na Início</div><div className="rs">“O que você gastou ou recebeu?”</div></span><Sw on={opts.field} onChange={(v) => setOpt("field", v)} /></div></div>
    </Screen>
  );
}

function CardsScreen({ pc }) {
  const [i, setI] = React.useState(0);
  const c = pc.cards[i]; const tot = window.cardTotal(c); const grand = pc.cards.reduce((s, x) => s + window.cardTotal(x), 0);
  return (
    <Screen title="Cartões" sub={"A pagar · R$ " + fmt(grand)}>
      <div className="ccar" onScroll={(e) => { const n = Math.round(e.currentTarget.scrollLeft / (e.currentTarget.offsetWidth - 40)); if (n !== i && pc.cards[n]) setI(n); }}>
        {pc.cards.map((x) => (
          <div key={x.id} className="ctile" style={{ background: x.gradient }}>
            <div className="ct-top"><b>{x.name}</b><span>{x.brand}</span></div>
            <div className="ct-k">Fatura aberta</div><div className="ct-v tnum">R$ {fmt(window.cardTotal(x))}</div>
            <div className="ct-bot"><span>•••• {x.last4}</span><span>Vence {x.dueLabel}</span></div>
          </div>
        ))}
      </div>
      <div className="dots">{pc.cards.map((x, j) => <i key={x.id} className={j === i ? "on" : ""} />)}</div>
      <div className="sec-h">Fatura<small>fecha em {c.closesInDays} dias · {Math.round((tot / c.limit) * 100)}% do limite</small></div>
      <div className="list">
        {c.items.map((it, j) => (
          <div key={j} className="row">
            <span className="mono">{it.name.slice(0, 1)}</span>
            <span className="rt"><div className="rk">{it.name}{it.inst && <span className="badge" style={{ marginLeft: 6 }}>{it.inst}</span>}</div><div className={"rs" + (it.tag ? "" : " wrn")}>{ddmm(it.date)} · {it.tag || "classificar"}</div></span>
            <span className="tnum" style={{ fontWeight: 600 }}>R$ {fmt(it.amount)}</span>
          </div>
        ))}
      </div>
    </Screen>
  );
}
Object.assign(window, { MaisScreen, CategoriasScreen, AtalhosScreen, CardsScreen });
