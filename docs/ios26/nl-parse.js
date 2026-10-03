// PocketCounter — parser de linguagem natural (pt-BR) para lançamento rápido.
// Puro JS, sem dependências: usado pelo app web e pelo app Android.
//
// NLQ.parse(texto, ctx) → {
//   ok, type, amount, name, date, method, cardId, tagIds, suggest,
//   confidence: "alta" | "media" | "baixa",
//   fields: [{key,label,value,from}],   // o que foi entendido, para os chips
//   ask: null | { key, question, options:[{label,value}], allowSkip }
// }
// ctx = { cards, tags, contexts, today }
//
// Regra de produto: se `ask` existe, a UI PERGUNTA antes de salvar.
// Sem `ask` e confiança alta → salva direto (com desfazer).

window.NLQ = (function () {
  const deaccent = (s) => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  const norm = (s) => deaccent(String(s || "").toLowerCase()).replace(/\s+/g, " ").trim();
  const cap = (s) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);
  const iso = (d) => d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");

  const METHOD_LABEL = { credit: "Crédito", debit: "Débito", pix: "Pix", cash: "Dinheiro", crypto: "Cripto" };

  // ── tipo (despesa / receita) ──────────────────────────────────────────────
  const INCOME_RE = /\b(recebi|receber|recebido|recebimento|entrou|caiu|caiu na conta|ganhei|me pagaram|pagaram|creditou|creditado|rendeu|rendimento|dividendo|dividendos|salario|reembolso|reembolsaram|restituicao|vendi)\b/;
  const EXPENSE_RE = /\b(paguei|pagar|pago|pagamento|gastei|gasto|comprei|compra|mandei|enviei|transferi|torrei|assinei|débito|debitou|saiu|投)\b/;

  // ── palavra-chave → nome de tag existente ─────────────────────────────────
  const TAG_HINTS = [
    [/\b(supermercado|mercado|atacad\w*|hortifruti|feira|acougue|padaria)\b/, "supermercado"],
    [/\b(restaurante|almoc\w*|jantar|lanche|ifood|rappi|pizza|hamburg\w*|sushi|cafe|bar)\b/, "restaurante"],
    [/\b(gasolina|combustivel|posto|etanol|alcool|diesel|abasteci\w*)\b/, "combustível"],
    [/\b(mecanic\w*|oficina|pneu|revisao|alinhament\w*|troca de oleo|funilaria)\b/, "manutenção veículo"],
    [/\b(farmacia|drogaria|drogasil|remedio|medicament\w*)\b/, "farmácia"],
    [/\b(luz|energia|agua|internet|condominio|aluguel|faxina|diarista|gas|iptu|limpeza)\b/, "serviços domésticos"],
    [/\b(imposto\w*|das|inss|contabilidade|contador|darf|tributo\w*)\b/, "impostos"],
    [/\b(netflix|spotify|assinatura|prime|hbo|max|disney|youtube|icloud|dropbox|plano)\b/, "assinatura"],
    [/\b(roupa\w*|shopping|presente|magalu|amazon|americanas|shopee|mercado livre|compras)\b/, "compras"],
    [/\b(viagem|hotel|passagem|airbnb|voo|hospedagem|pousada)\b/, "viagem"],
    [/\b(celular|notebook|fone|eletronic\w*|tv|monitor|teclado|mouse)\b/, "eletrônicos"],
    [/\b(salario|holerite|pagamento mensal)\b/, "salário"],
    [/\b(freela\w*|freelance|projeto|servico prestado|consultoria)\b/, "freelance"],
  ];

  // palavra-chave → contexto provável, quando nenhuma tag casa exatamente.
  // A chave é comparada de forma tolerante ao nome do contexto do app
  // ("saude" casa com "Saúde" e com "Saúde e Bem-estar").
  const CTX_HINTS = [
    [/\b(estacionament\w*|uber|99|taxi|onibus|metro|pedagio|zona azul|corrida)\b/, "transporte"],
    [/\b(consulta|medic\w*|exame|dentista|terapia|psicolog\w*|vacina|veterinari\w*|cachorro|gato|pet|racao)\b/, "saude"],
    [/\b(cinema|show|ingresso|jogo|steam|balada|parque)\b/, "lazer"],
    [/\b(escritorio|funcionario|cliente|nota fiscal|equipamento)\b/, "empresa"],
    [/\b(aluguel|condominio|luz|energia|agua|gas|iptu)\b/, "moradia"],
  ];

  // ── valor ─────────────────────────────────────────────────────────────────
  function parseAmount(t) {
    // "2 mil", "1,5 mil"
    let m = t.match(/(\d+(?:[.,]\d+)?)\s*mil\b/);
    if (m) return { value: parseFloat(m[1].replace(",", ".")) * 1000, range: [m.index, m.index + m[0].length] };
    // R$ 1.250,50 · 1250,50 · 1.250 · 250 · 10 reais · 10 pila/conto
    m = t.match(/(?:r\$\s*)?(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:,\d{1,2})?|\d+(?:\.\d{1,2})?)\s*(?:reais|real|rs|pila|conto|contos)?/);
    if (!m) return null;
    let raw = m[1];
    // pt-BR: ponto = milhar quando seguido de 3 dígitos; vírgula = decimal
    if (/,/.test(raw)) raw = raw.replace(/\./g, "").replace(",", ".");
    else if (/\.\d{3}\b/.test(raw)) raw = raw.replace(/\./g, "");
    const v = parseFloat(raw);
    if (!isFinite(v) || v <= 0) return null;
    return { value: v, range: [m.index, m.index + m[0].length] };
  }

  // ── data ──────────────────────────────────────────────────────────────────
  const WD = { domingo: 0, segunda: 1, terca: 2, quarta: 3, quinta: 4, sexta: 5, sabado: 6 };
  function parseDate(t, today) {
    const base = today ? new Date(today + "T12:00:00") : new Date();
    const mk = (d) => iso(d);
    const shift = (n) => { const d = new Date(base); d.setDate(d.getDate() + n); return d; };
    let m;
    if ((m = t.match(/\bhoje\b/))) return { value: mk(base), label: "hoje", range: [m.index, m.index + m[0].length] };
    if ((m = t.match(/\banteontem\b/))) return { value: mk(shift(-2)), label: "anteontem", range: [m.index, m.index + m[0].length] };
    if ((m = t.match(/\bontem\b/))) return { value: mk(shift(-1)), label: "ontem", range: [m.index, m.index + m[0].length] };
    if ((m = t.match(/\bamanha\b/))) return { value: mk(shift(1)), label: "amanhã", range: [m.index, m.index + m[0].length] };
    if ((m = t.match(/\b(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?/))) {
      const y = m[3] ? (m[3].length === 2 ? 2000 + +m[3] : +m[3]) : base.getFullYear();
      const d = new Date(y, +m[2] - 1, +m[1], 12);
      return { value: mk(d), label: m[0], range: [m.index, m.index + m[0].length] };
    }
    if ((m = t.match(/\bdia\s+(\d{1,2})\b/))) {
      const d = new Date(base); d.setDate(+m[1]);
      return { value: mk(d), label: "dia " + m[1], range: [m.index, m.index + m[0].length] };
    }
    if ((m = t.match(/\b(semana passada)\b/))) return { value: mk(shift(-7)), label: "semana passada", range: [m.index, m.index + m[0].length] };
    if ((m = t.match(/\b(mes passado)\b/))) { const d = new Date(base); d.setMonth(d.getMonth() - 1); return { value: mk(d), label: "mês passado", range: [m.index, m.index + m[0].length] }; }
    if ((m = t.match(/\b(esse|este|neste|nesse)\s+mes\b/))) return { value: mk(base), label: "este mês", range: [m.index, m.index + m[0].length] };
    if ((m = t.match(/\b(domingo|segunda|terca|quarta|quinta|sexta|sabado)(?:-feira)?\b/))) {
      const target = WD[m[1]]; const d = new Date(base);
      let diff = (d.getDay() - target + 7) % 7; if (diff === 0) diff = 7;
      d.setDate(d.getDate() - diff);
      return { value: mk(d), label: m[1], range: [m.index, m.index + m[0].length] };
    }
    return null;
  }

  // ── método + cartão ───────────────────────────────────────────────────────
  function parseMethod(t, cards) {
    const hit = (re) => { const m = t.match(re); return m ? [m.index, m.index + m[0].length] : null; };
    let r;
    // cartão nomeado ganha de tudo — casa o nome inteiro ou qualquer palavra dele
    // ("Cartão NuBank" → "nubank", "no cartão nubank", "Cartão Itaú" → "itau")
    const cands = [];
    for (const c of cards || []) {
      const full = norm(c.name);
      cands.push({ id: c.id, s: full });
      full.split(/\s+/).forEach((w) => { if (w.length >= 3 && w !== "cartao" && w !== "conta") cands.push({ id: c.id, s: w }); });
    }
    cands.sort((a, b) => b.s.length - a.s.length);
    for (const c of cands) {
      const m = t.match(new RegExp("\\b" + c.s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\b"));
      if (m) return { method: "credit", cardId: c.id, range: [m.index, m.index + m[0].length], ambiguous: false };
    }
    if ((r = hit(/\bno\s+credito\b|\bcredito\b|\bcartao de credito\b|\bparcelad\w*\b/))) return { method: "credit", cardId: null, range: r, ambiguous: true };
    if ((r = hit(/\b(no|com|de)?\s*cartao\b/))) return { method: "credit", cardId: null, range: r, ambiguous: true };
    if ((r = hit(/\bpix\b/))) return { method: "pix", cardId: null, range: r, ambiguous: false };
    if ((r = hit(/\b(debito|no debito|cartao de debito)\b/))) return { method: "debit", cardId: null, range: r, ambiguous: false };
    if ((r = hit(/\b(dinheiro|especie|em maos|cash)\b/))) return { method: "cash", cardId: null, range: r, ambiguous: false };
    if ((r = hit(/\b(cripto|bitcoin|btc|usdt|stablecoin)\b/))) return { method: "crypto", cardId: null, range: r, ambiguous: false };
    if ((r = hit(/\b(boleto|transferencia|ted|doc|debito automatico)\b/))) return { method: "debit", cardId: null, range: r, ambiguous: false };
    return { method: null, cardId: null, range: null, ambiguous: false };
  }

  // ── nome do lançamento ────────────────────────────────────────────────────
  const STRIP_VERBS = /\b(paguei|pagar|pago|pagamento de|gastei|gasto de|comprei|compra de|mandei|enviei|transferi|recebi|receber|recebido|recebimento de|entrou|caiu|ganhei|me pagaram|pagaram|creditou|assinei|torrei|foi|fiz|numa|num|uma|um)\b/gi;
  const EDGE = /^(?:de|do|da|dos|das|em|no|na|nos|nas|num|numa|com|para|pra|pras|pro|pros|por|a|o|as|os|e|que|meu|minha|uns|umas)\s+|\s+(?:de|do|da|em|no|na|com|para|pra|pro|por|e|a|o)$/gi;

  function buildName(original, ranges, method) {
    let s = original;
    // remove os trechos já interpretados (do fim para o começo, preserva índices)
    ranges.filter(Boolean).sort((a, b) => b[0] - a[0]).forEach(([a, b]) => { s = s.slice(0, a) + " " + s.slice(b); });
    s = s.replace(/r\$/gi, " ").replace(/\s+/g, " ").trim();
    const low = norm(s);
    // beneficiário: "para minha esposa" / "pra joão" / "pro João" / "pros meus pais"
    const ben = low.match(/\b(?:para|pra|pras|pro|pros)\s+(.{2,40})$/);
    let out;
    if (ben) {
      const who = s.slice(s.length - ben[1].length).trim().replace(/[.,;]$/, "");
      out = (METHOD_LABEL[method] || "Pagamento") + " para " + who;
    } else {
      let t = s.replace(STRIP_VERBS, " ").replace(/\s+/g, " ").trim();
      let prev = null; while (t !== prev) { prev = t; t = t.replace(EDGE, "").trim(); }
      t = t.replace(/[.,;]+$/, "").trim();
      out = t || METHOD_LABEL[method] || "Lançamento";
    }
    return cap(out.replace(/\s{2,}/g, " "));
  }

  // ── tags ──────────────────────────────────────────────────────────────────
  function matchTags(low, ctx, type) {
    const tags = (ctx.tags || []).filter((t) => (t.kind || "expense") === type);
    // 1) nome da tag citado literalmente
    for (const t of tags) { if (low.includes(norm(t.name))) return { tagIds: [t.id], suggest: null }; }
    // 2) sinônimo → nome de tag existente
    for (const [re, tagName] of TAG_HINTS) {
      if (re.test(low)) {
        const t = tags.find((x) => norm(x.name) === norm(tagName));
        if (t) return { tagIds: [t.id], suggest: null };
      }
    }
    // 3) contexto provável → sugere as tags dele (despesa) ou criar categoria (receita)
    for (const [re, ctxKey] of CTX_HINTS) {
      if (re.test(low)) {
        const k = norm(ctxKey);
        const c = (ctx.contexts || []).find((x) => { const n = norm(x.name); return n === k || n.includes(k) || k.includes(n); });
        if (c) return { tagIds: [], suggest: { contextId: c.id, contextName: c.name, options: tags.filter((t) => t.idContext === c.id).slice(0, 5) } };
      }
    }
    return { tagIds: [], suggest: null };
  }

  // há algum sinal de categoria na frase? (usado para inferir despesa sem verbo)
  function hasCategorySignal(low) {
    return TAG_HINTS.some(([re]) => re.test(low)) || CTX_HINTS.some(([re]) => re.test(low));
  }

  // ── parse ─────────────────────────────────────────────────────────────────
  function parse(text, ctx) {
    ctx = ctx || {};
    const original = String(text || "").trim();
    const low = norm(original);
    if (!low) return { ok: false, reason: "vazio" };

    const amt = parseAmount(low);
    const inc = INCOME_RE.test(low);
    const exp = EXPENSE_RE.test(low);
    const type = inc && !exp ? "income" : exp && !inc ? "expense" : inc ? "income" : "expense";

    const dt = parseDate(low, ctx.today);
    const pm = parseMethod(low, ctx.cards);
    // sem verbo, mas com método de pagamento ou categoria reconhecida → é despesa.
    // só perguntamos o tipo quando não há sinal nenhum ("68 ontem").
    const typeExplicit = inc || exp || !!pm.method || hasCategorySignal(low);
    const name = buildName(original, [amt && amt.range, dt && dt.range, pm.range], pm.method);
    let { tagIds, suggest } = matchTags(low, ctx, type);

    const fields = [];
    if (amt) fields.push({ key: "amount", label: "Valor", value: amt.value, from: "frase" });
    fields.push({ key: "type", label: type === "income" ? "Receita" : "Despesa", value: type, from: typeExplicit ? "frase" : "padrão" });
    fields.push({ key: "name", label: "Descrição", value: name, from: "frase" });
    fields.push({ key: "date", label: "Data", value: dt ? dt.value : (ctx.today || iso(new Date())), from: dt ? "frase" : "hoje" });
    if (pm.method) fields.push({ key: "method", label: "Pagamento", value: pm.method, from: "frase" });
    if (tagIds.length) fields.push({ key: "tags", label: "Categoria", value: tagIds, from: "frase" });

    // ── perguntar de volta: só quando é realmente necessário ──
    let ask = null;
    if (!amt) {
      ask = { key: "amount", question: "Qual foi o valor?", options: null, allowSkip: false };
    } else if (pm.method === "credit" && pm.ambiguous && (ctx.cards || []).length > 1) {
      ask = {
        key: "cardId",
        question: "Em qual cartão?",
        options: (ctx.cards || []).map((c) => ({ label: c.name, value: c.id, grad: c.grad })),
        allowSkip: true,
      };
    } else if (!typeExplicit && amt) {
      ask = {
        key: "type",
        question: "É uma despesa ou uma receita?",
        options: [{ label: "Despesa", value: "expense" }, { label: "Receita", value: "income" }],
        allowSkip: false,
      };
    }

    // receita sem categoria conhecida → oferece criar (ex.: "dividendos")
    if (!tagIds.length && !suggest && type === "income" && name && name.length <= 24) {
      suggest = { createName: name.toLowerCase(), kind: "income", options: [] };
    }

    // cartão único resolve sozinho
    let cardId = pm.cardId;
    if (pm.method === "credit" && !cardId && (ctx.cards || []).length === 1) cardId = ctx.cards[0].id;

    const confidence = ask ? "baixa" : (tagIds.length ? "alta" : "media");

    return {
      ok: true, type, amount: amt ? amt.value : null, name,
      date: dt ? dt.value : (ctx.today || iso(new Date())),
      dateLabel: dt ? dt.label : "hoje",
      method: pm.method, cardId, tagIds, suggest, fields, ask, confidence,
      raw: original,
    };
  }

  return { parse, METHOD_LABEL, norm };
})();
