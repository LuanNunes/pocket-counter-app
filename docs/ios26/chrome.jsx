// iOS 26 chrome — device, status bar, Dynamic Island, Liquid Glass bars, sheets, menus, icons.
const GI_P = {
  house: { f: ["M12 3.2 2.8 11.1a.9.9 0 0 0 .6 1.6H5v7.4c0 .5.4.9.9.9H10v-5.5h4V21h4.1c.5 0 .9-.4.9-.9v-7.4h1.6a.9.9 0 0 0 .6-1.6z"] },
  list: { s: ["M9 6.5h11", "M9 12h11", "M9 17.5h11", "M4.5 6.5h.01", "M4.5 12h.01", "M4.5 17.5h.01"], w: 2.2 },
  card: { s: ["M3 6.5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M3 9.5h18", "M6.5 15h3"] },
  moreC: { s: ["M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M8 12h.01", "M12 12h.01", "M16 12h.01"] },
  more: { s: ["M5.5 12h.01", "M12 12h.01", "M18.5 12h.01"], w: 3.2 },
  spark: { f: ["M12 2.5l1.7 5.1a3 3 0 0 0 1.9 1.9l5.1 1.7-5.1 1.7a3 3 0 0 0-1.9 1.9L12 21l-1.7-5.1a3 3 0 0 0-1.9-1.9L3.3 12.3l5.1-1.7a3 3 0 0 0 1.9-1.9z"] },
  mic: { s: ["M9 5.5a3 3 0 0 1 6 0v5.5a3 3 0 0 1-6 0z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21"] },
  chart: { s: ["M4 20h16", "M7 16v-4", "M12 16V7", "M17 16v-7"] },
  pie: { s: ["M12 3v9h9", "M21 12a9 9 0 1 1-9-9"] },
  tag: { s: ["M3.5 3.5h7.6L20.5 13a1.5 1.5 0 0 1 0 2.1l-5.4 5.4a1.5 1.5 0 0 1-2.1 0L3.5 11z", "M7.5 7.5h.01"] },
  cal: { s: ["M4 6.5h16v14H4z", "M4 10.5h16", "M8 3v4", "M16 3v4"] },
  clock: { s: ["M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 7.5V12l3 2"] },
  pin: { s: ["M12 16v5", "M8.5 3.5h7l-1.2 6.2 2.7 2.3v1.5H7v-1.5l2.7-2.3z"] },
  gear: { s: ["M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z", "M19.4 13a7.5 7.5 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.5 7.5 0 0 0-1.7-1l-.4-2.5h-3.8l-.4 2.5a7.5 7.5 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.6a7.5 7.5 0 0 0 0 2l-2 1.6 2 3.4 2.4-1a7.5 7.5 0 0 0 1.7 1l.4 2.5h3.8l.4-2.5a7.5 7.5 0 0 0 1.7-1l2.4 1 2-3.4z"] },
  check: { s: ["m5 12.5 4.5 4.5L19 7"], w: 2.4 },
  x: { s: ["M6.5 6.5l11 11", "M17.5 6.5l-11 11"], w: 2.2 },
  plus: { s: ["M12 5v14", "M5 12h14"], w: 2.2 },
  chevR: { s: ["m9.5 5.5 6.5 6.5-6.5 6.5"], w: 2.2 },
  chevL: { s: ["m14.5 5.5-6.5 6.5 6.5 6.5"], w: 2.4 },
  chevD: { s: ["m6 9.5 6 6 6-6"], w: 2.2 },
  chevUD: { s: ["m8 9.5 4-4 4 4", "m8 14.5 4 4 4-4"], w: 2.2 },
  search: { s: ["M10.5 17.5a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "m20 20-4.5-4.5"], w: 2.1 },
  trash: { s: ["M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"] },
  grip: { s: ["M5 8.5h14", "M5 12h14", "M5 15.5h14"], w: 2 },
  warn: { s: ["M12 9v4", "M12 17h.01", "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"], w: 2 },
  undo: { s: ["M3 12a9 9 0 1 0 3.1-6.8", "M3 3v5h5"], w: 2 },
  up: { s: ["M12 19V5", "m5.5 11.5 6.5-6.5 6.5 6.5"], w: 2.4 },
  arrUR: { s: ["M7 17 17 7", "M8 7h9v9"], w: 2 },
  arrDL: { s: ["M17 7 7 17", "M16 17H7V8"], w: 2 },
  lock: { f: ["M7 10V8a5 5 0 0 1 10 0v2h.5A1.5 1.5 0 0 1 19 11.5v8a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19.5v-8A1.5 1.5 0 0 1 6.5 10zm2 0h6V8a3 3 0 0 0-6 0z"] },
  flash: { s: ["M8 3h8v3l-2 3v12h-4V9L8 6z", "M12 13v2"] },
  camera: { s: ["M4 8h3l2-2.5h6L17 8h3v11H4z", "M12 16.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"] },
  bolt: { f: ["M13 2 4.5 13.5H11l-1 8.5L19.5 10H13z"] },
  widget: { s: ["M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h16v7H4z"] },
  island: { s: ["M7 9h10a3 3 0 0 1 0 6H7a3 3 0 0 1 0-6z"] },
  action: { s: ["M9 3h6a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z", "M3 9v4"] },
  person: { s: ["M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4.5 20a7.5 7.5 0 0 1 15 0z"] },
  book: { s: ["M4 5a2 2 0 0 1 2-2h13v15H6a2 2 0 0 0-2 2z", "M4 20a2 2 0 0 1 2-2h13"] },
  recur: { s: ["M21 12a9 9 0 1 1-3.1-6.8", "M21 3v5h-5"] },
  moon: { s: ["M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"] },
  edit: { s: ["M16 3.5 20.5 8 8 20.5l-4.5 1 1-4.5z"] },
};
function GI({ n, s = 20, w, style }) {
  const d = GI_P[n]; if (!d) return null;
  const c = { width: s, height: s, viewBox: "0 0 24 24", "aria-hidden": true, style };
  if (d.f) return <svg {...c} fill="currentColor">{d.f.map((p, i) => <path key={i} d={p} />)}</svg>;
  return <svg {...c} fill="none" stroke="currentColor" strokeWidth={w || d.w || 1.8} strokeLinecap="round" strokeLinejoin="round">{d.s.map((p, i) => <path key={i} d={p} />)}</svg>;
}

function useFit(w = 402, h = 874) {
  const [k, setK] = React.useState(1);
  React.useEffect(() => {
    const f = () => { const s = Math.min(1, (innerHeight - 28) / h, (innerWidth - 28) / w); setK(s > 0 ? s : 1); window.__PC_SCALE = s > 0 ? s : 1; };
    f(); addEventListener("resize", f); return () => removeEventListener("resize", f);
  }, []);
  return k;
}
function Phone({ dark, children, outside }) {
  const k = useFit();
  return (
    <div className="phone-fit" style={{ transform: "scale(" + k + ")" }}>
      <div className={"phone" + (dark ? " dark" : "")}>{outside}<div className="screen">{children}</div></div>
    </div>
  );
}
function StatusBar({ light }) {
  return (
    <div className={"sbar" + (light ? " light" : "")}>
      <span className="tnum">9:41</span>
      <span className="sb-r">
        <svg width="19" height="12" viewBox="0 0 18 12" fill="currentColor"><rect x="0" y="7.5" width="3" height="4.5" rx="1" /><rect x="4.5" y="5" width="3" height="7" rx="1" /><rect x="9" y="2.5" width="3" height="9.5" rx="1" /><rect x="13.5" y="0" width="3" height="12" rx="1" /></svg>
        <svg width="17" height="12" viewBox="0 0 17 12" fill="currentColor"><path d="M8.5 2.2c2.6 0 5 1 6.8 2.7l1.4-1.5A11.4 11.4 0 0 0 8.5.2 11.4 11.4 0 0 0 .3 3.4l1.4 1.5A9.4 9.4 0 0 1 8.5 2.2z" /><path d="M8.5 5.9c1.6 0 3 .6 4.1 1.6l1.4-1.5A8 8 0 0 0 8.5 3.9a8 8 0 0 0-5.5 2.1l1.4 1.5A6 6 0 0 1 8.5 5.9z" /><path d="M8.5 9.5 11 7a3.6 3.6 0 0 0-5 0z" /></svg>
        <svg width="27" height="13" viewBox="0 0 27 13" fill="none"><rect x=".5" y=".5" width="22" height="12" rx="3.8" stroke="currentColor" strokeOpacity=".4" /><rect x="2" y="2" width="17" height="9" rx="2.2" fill="currentColor" /><path d="M24.5 4.5c1.3.4 1.3 3.6 0 4z" fill="currentColor" fillOpacity=".5" /></svg>
      </span>
    </div>
  );
}

// Screen scaffold: toolbar row in glass + large title that collapses into the inline title.
function Screen({ title, sub, left, right, children, head }) {
  const [sc, setSc] = React.useState(false);
  return (
    <div className="scr">
      <div className="scroll" onScroll={(e) => { const s = e.currentTarget.scrollTop > 36; if (s !== sc) setSc(s); }}>
        <div className="lt-wrap"><h1 className="lt">{title}</h1>{sub && <div className="lt-sub">{sub}</div>}</div>
        {children}
      </div>
      <div className={"edge" + (sc ? " on" : "")} />
      <div className="topbar">
        <div className="tb-l">{left}</div>
        <div className={"tb-t" + (sc ? " on" : "")}>{title}</div>
        <div className="tb-r">{right}</div>
      </div>
    </div>
  );
}
const GBtn = ({ n, onClick, tinted, label, s = 20 }) => (
  <button className={"gbtn glass" + (tinted ? " tinted" : "")} onClick={onClick} aria-label={label}><GI n={n} s={s} /></button>
);
const BackBtn = ({ onClick }) => <GBtn n="chevL" onClick={onClick} label="Voltar" s={22} />;

function TabBar({ active, onNav }) {
  const tabs = [["inicio", "Início", "house"], ["transacoes", "Transações", "list"], ["cartoes", "Cartões", "card"], ["mais", "Mais", "moreC"]];
  return (
    <>
      <div className="tabfade" />
      <nav className="tabbar glass">
        {tabs.map(([id, lb, ic]) => (
          <button key={id} className={"tab" + (active === id ? " on" : "")} onClick={() => onNav(id)}>
            <GI n={ic} s={25} />{lb}
          </button>
        ))}
      </nav>
    </>
  );
}

function useMount(open, ms = 420) {
  const [m, setM] = React.useState(open);
  const [on, setOn] = React.useState(false);
  React.useEffect(() => {
    if (open) { setM(true); const t = setTimeout(() => setOn(true), 20); return () => clearTimeout(t); }
    setOn(false); const t = setTimeout(() => setM(false), ms); return () => clearTimeout(t);
  }, [open]);
  return [m, on];
}
function GSheet({ open, onClose, title, children, footer, large, left, right }) {
  const [m, on] = useMount(open);
  if (!m) return null;
  return (
    <div className={"scrim" + (on ? " on" : "")} onClick={onClose}>
      <div className={"gsheet" + (large ? " large" : "") + (on ? " on" : "")} onClick={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <div className="grab" />
        <div className="sh-hd">
          {left === undefined ? <button className="gbtn" onClick={onClose} aria-label="Fechar"><GI n="x" s={18} /></button> : left}
          <div className="sh-t">{title}</div>
          {right || <span style={{ width: 40 }} />}
        </div>
        <div className="sh-body">{children}</div>
        {footer && <div className="sh-foot">{footer}</div>}
      </div>
    </div>
  );
}
function GMenu({ open, onClose, items, top = 104, right = 16, left }) {
  if (!open) return null;
  const pos = left != null ? { top, left, transformOrigin: "top left" } : { top, right };
  return (
    <div className="mscrim" onClick={onClose}>
      <div className="menu" style={pos} onClick={(e) => e.stopPropagation()}>
        {items.map((it, i) => it.sep ? <div key={i} className="msep" /> : it.head ? <div key={i} className="mh">{it.head}</div> : (
          <button key={i} className={"mi" + (it.dst ? " dst" : "")} onClick={() => { it.onClick && it.onClick(); if (!it.keep) onClose(); }}>
            <span className="ck">{it.on && <GI n="check" s={17} />}</span>{it.label}
            {it.icon && <span className="mic"><GI n={it.icon} s={19} /></span>}
          </button>
        ))}
      </div>
    </div>
  );
}
function GAlert({ open, title, msg, actions }) {
  if (!open) return null;
  return (
    <>
      <div className="ascrim" />
      <div className="alert" role="alertdialog"><h3>{title}</h3>{msg && <p>{msg}</p>}
        <div className="acts">{actions.map((a, i) => <button key={i} className={"btn " + (a.dst ? "dst" : a.pri ? "pri" : "sec")} onClick={a.onClick}>{a.label}</button>)}</div>
      </div>
    </>
  );
}
const Seg = ({ value, options, onChange, sm }) => (
  <div className={"seg" + (sm ? " sm" : "")} role="tablist">
    {options.map(([v, lb]) => <button key={v} className={value === v ? "on" : ""} onClick={() => onChange(v)} role="tab" aria-selected={value === v}>{lb}</button>)}
  </div>
);
const Sw = ({ on, onChange }) => <button className={"sw" + (on ? " on" : "")} role="switch" aria-checked={on} onClick={() => onChange(!on)} />;

Object.assign(window, { GI, Phone, StatusBar, Screen, GBtn, BackBtn, TabBar, GSheet, GMenu, GAlert, Seg, Sw, useMount });
