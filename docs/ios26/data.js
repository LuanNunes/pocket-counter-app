// Sample data for the PocketCounter prototype.
// Shape mirrors the backend DTOs (TransactionDto, SourceDto, TagDto + Context).
//
// Notification statuses:
//   "auto"           → backend classified everything confidently
//   "needs-tags"     → core fields OK, missing tags only (skippable step)
//   "needs-review"   → backend couldn't confidently classify; full wizard

// ── Métodos de pagamento — ENUM FIXO (não editável) ─────────────────────────
// Só o crédito tem consequência: a compra vai para a fatura de um cartão.
window.PAYMENT_METHODS = [
  { id: "credit", label: "Crédito" },
  { id: "debit",  label: "Débito" },
  { id: "pix",    label: "Pix" },
  { id: "cash",   label: "Dinheiro" },
  { id: "crypto", label: "Cripto" },
];
window.METHOD_LABEL = Object.fromEntries(window.PAYMENT_METHODS.map((m) => [m.id, m.label]));

// ── Legacy (amostras antigas) → novo modelo ─────────────────────────────────
// idPaymentSource antigo → { método fixo, cartão de crédito (ou null) }
window.LEGACY_PAY = {
  itau:   { method: "credit", cardId: "itau" },
  nubank: { method: "credit", cardId: "nubank" },
  cc:     { method: "pix",    cardId: null },
  pix:    { method: "pix",    cardId: null },
};
// migra uma transação/sugestão antiga {idSource,idPaymentSource} → novo shape
window.legacyTx = function (t) {
  const src = (window.SAMPLE_SOURCES || []).find((s) => s.id === t.idSource);
  const pay = window.LEGACY_PAY[t.idPaymentSource] || { method: null, cardId: null };
  const seriesId = (window.SRC_TO_SERIES || {})[t.idSource] || null;
  return {
    ...t,
    name: src ? src.name : (t.merchantRaw || t.name || "—"),
    paymentMethod: pay.method,
    cardId: pay.cardId,
    seriesId,
    fixo: !!(seriesId || (src && src.refDayRecurring)),
    recurrenceDay: src ? src.refDayRecurring || null : null,
  };
};

// ── Payment sources (legado — só lookup de nome p/ migração) ────────────────
window.SAMPLE_PAYMENT_METHODS = [
  { id: "itau",   name: "Cartão Itaú",   sub: "PERSON BLACK CASHBAC •3685", kind: "credit",   color: "oklch(0.45 0.04 50)" },
  { id: "nubank", name: "Cartão NuBank", sub: "Mastercard •0712",            kind: "credit",   color: "oklch(0.48 0.18 300)" },
  { id: "cc",     name: "Conta Corrente", sub: "Bradesco •48-1",             kind: "checking", color: "oklch(0.55 0.15 25)" },
  { id: "pix",    name: "Conta Pix",      sub: "Inter •772",                 kind: "checking", color: "oklch(0.62 0.18 35)" },
];

// ── Sources (idSource) ──────────────────────────────────────────────────────
// Each Source is tied to ONE payment method and is either income or expense
// (or both). Recurring sources carry a refDayRecurring; non-recurring are
// classified as "merchant templates" the user has named.
window.SAMPLE_SOURCES = [
  // expense sources
  { id: "src-ifood-nu",   name: "iFood",            idPaymentSource: "nubank", allowsExpense: true,  allowsIncome: false, refDayRecurring: null },
  { id: "src-uber",       name: "Uber",             idPaymentSource: "nubank", allowsExpense: true,  allowsIncome: false, refDayRecurring: null },
  { id: "src-99",         name: "99",               idPaymentSource: "nubank", allowsExpense: true,  allowsIncome: false, refDayRecurring: null },
  { id: "src-pao",        name: "Pão de Açúcar",    idPaymentSource: "itau",   allowsExpense: true,  allowsIncome: false, refDayRecurring: null },
  { id: "src-magalu",     name: "Magazine Luiza",   idPaymentSource: "nubank", allowsExpense: true,  allowsIncome: false, refDayRecurring: null },
  { id: "src-spotify",    name: "Spotify",          idPaymentSource: "nubank", allowsExpense: true,  allowsIncome: false, refDayRecurring: 15, amount: 21.90 },
  { id: "src-aluguel",    name: "Aluguel",          idPaymentSource: "cc",     allowsExpense: true,  allowsIncome: false, refDayRecurring: 10, amount: 2400.00 },
  { id: "src-enel",       name: "Enel — energia",   idPaymentSource: "cc",     allowsExpense: true,  allowsIncome: false, refDayRecurring: 19, amount: 234.50 },
  // income sources
  { id: "src-salario",    name: "Salário",          idPaymentSource: "cc",     allowsExpense: false, allowsIncome: true,  refDayRecurring: 5,  amount: 6450.00 },
  { id: "src-freela-sz",  name: "Freela Studio Z",  idPaymentSource: "cc",     allowsExpense: false, allowsIncome: true,  refDayRecurring: null },
  // shared
  { id: "src-pix-pessoa", name: "PIX entre contas", idPaymentSource: "pix",    allowsExpense: true,  allowsIncome: true,  refDayRecurring: null },
];

// ── Contexts — só de DESPESA (receita usa categorias de renda planas) ───────
window.SAMPLE_CONTEXTS = [
  { id: "ctx-alimentacao", name: "Alimentação", color: "oklch(0.6 0.16 30)",  kind: "expense" },
  { id: "ctx-transporte",  name: "Transporte",  color: "oklch(0.55 0.14 240)", kind: "expense" },
  { id: "ctx-moradia",     name: "Moradia",     color: "oklch(0.55 0.13 160)", kind: "expense" },
  { id: "ctx-saude",       name: "Saúde",       color: "oklch(0.6 0.15 350)",  kind: "expense" },
  { id: "ctx-empresa",     name: "Empresa",     color: "oklch(0.55 0.10 280)", kind: "expense" },
  { id: "ctx-lazer",       name: "Lazer",       color: "oklch(0.6 0.16 70)",   kind: "expense" },
  { id: "ctx-compras",     name: "Compras",     color: "oklch(0.62 0.15 320)", kind: "expense" },
];

// ── Tags (idContext groups them) ───────────────────────────────────────────
window.SAMPLE_TAGS = [
  { id: "tag-supermercado", name: "supermercado",         idContext: "ctx-alimentacao" },
  { id: "tag-restaurante",  name: "restaurante",          idContext: "ctx-alimentacao" },
  { id: "tag-delivery",     name: "delivery",             idContext: "ctx-alimentacao" },
  { id: "tag-combustivel",  name: "combustível",          idContext: "ctx-transporte" },
  { id: "tag-app-mobil",    name: "app de mobilidade",    idContext: "ctx-transporte" },
  { id: "tag-manut-veic",   name: "manutenção veículo",   idContext: "ctx-transporte" },
  { id: "tag-aluguel",      name: "aluguel",              idContext: "ctx-moradia" },
  { id: "tag-contas-casa",  name: "contas da casa",       idContext: "ctx-moradia" },
  { id: "tag-servicos-dom", name: "serviços domésticos",  idContext: "ctx-moradia" },
  { id: "tag-streaming",    name: "streaming",            idContext: "ctx-lazer" },
  { id: "tag-cinema",       name: "cinema",               idContext: "ctx-lazer" },
  { id: "tag-impostos",     name: "impostos",             idContext: "ctx-empresa" },
  { id: "tag-contabilidade", name: "contabilidade",        idContext: "ctx-empresa" },
  { id: "tag-farmacia",     name: "farmácia",             idContext: "ctx-saude" },
  { id: "tag-academia",     name: "academia",             idContext: "ctx-saude" },
  { id: "tag-varejo",       name: "varejo",               idContext: "ctx-compras" },
  { id: "tag-eletronicos",  name: "eletrônicos",          idContext: "ctx-compras" },
  { id: "tag-viagem",       name: "viagem",               idContext: "ctx-compras" },
  { id: "tag-salario",      name: "salário",   kind: "income", idContext: null, color: "oklch(0.55 0.13 160)" },
  { id: "tag-freelance",    name: "freelance", kind: "income", idContext: null, color: "oklch(0.5 0.14 250)" },
];

// ── Contas Fixas (séries recorrentes) — categorização + valor/dia padrão compartilhados ─
window.SAMPLE_SERIES = [
  { id: "s-aluguel", name: "Aluguel",        direction: "expense", tagIds: ["tag-aluguel"],     defaultAmount: 2400.00, day: 10 },
  { id: "s-enel",    name: "Enel — energia",  direction: "expense", tagIds: ["tag-contas-casa"], defaultAmount: 234.50,  day: 19 },
  { id: "s-spotify", name: "Spotify",        direction: "expense", tagIds: ["tag-streaming"],   defaultAmount: 21.90,   day: 15 },
  { id: "s-salario", name: "Salário",        direction: "income",  tagIds: ["tag-salario"],    defaultAmount: 6450.00, day: 5  },
];
window.SRC_TO_SERIES = { "src-aluguel": "s-aluguel", "src-enel": "s-enel", "src-spotify": "s-spotify", "src-salario": "s-salario" };

// ── Notifications ───────────────────────────────────────────────────────────
// `suggestions` mirrors what the /classify endpoint would return:
//   idPaymentSource, idSource, tagIds[]
window.SAMPLE_NOTIFICATIONS = [
  {
    id: "n7",
    app: "Banco Itaú",
    channel: "SMS",
    time: "agora",
    received: "18:41",
    text: "Compra aprovada no seu PERSON BLACK CASHBAC final 3685 - IFD*A M GUILHERME CORR valor R$ 153,98 em 16/05, as 18h41.",
    status: "needs-review",
    parsed: {
      type: "expense",
      amount: 153.98,
      date: "2026-05-16",
      merchantRaw: "IFD*A M GUILHERME CORR",
      paymentHint: "PERSON BLACK CASHBAC final 3685",
    },
    suggestions: {
      idPaymentSource: "itau",
      idSource: null,
      tagIds: [],
    },
    tokens: [
      { t: "Compra aprovada", role: "type", value: "expense" },
      { t: " no seu " },
      { t: "PERSON BLACK CASHBAC final 3685", role: "payment", value: "itau" },
      { t: " - " },
      { t: "IFD*A M GUILHERME CORR", role: "merchant" },
      { t: " valor " },
      { t: "R$ 153,98", role: "amount", value: 153.98 },
      { t: " em " },
      { t: "16/05", role: "date", value: "2026-05-16" },
      { t: ", as 18h41." },
    ],
  },
  {
    id: "n6",
    app: "Nubank",
    channel: "Push",
    time: "1m",
    received: "18:40",
    text: "Nubank: Compra aprovada de R$ 45,90 em IFOOD*RESTAURANTE",
    status: "needs-tags",
    parsed: {
      type: "expense",
      amount: 45.90,
      date: "2026-05-19",
      merchantRaw: "IFOOD*RESTAURANTE",
      paymentHint: "Nubank",
    },
    suggestions: {
      idPaymentSource: "nubank",
      idSource: "src-ifood-nu",
      tagIds: ["tag-delivery", "tag-restaurante"],
    },
    tokens: [
      { t: "Nubank", role: "payment", value: "nubank" },
      { t: ": Compra aprovada de " },
      { t: "R$ 45,90", role: "amount", value: 45.90 },
      { t: " em " },
      { t: "IFOOD*RESTAURANTE", role: "merchant", value: "src-ifood-nu" },
    ],
  },
  {
    id: "n5",
    app: "Inter",
    channel: "Push",
    time: "12m",
    received: "18:29",
    text: "PIX recebido de Maria Santos: R$ 800,00",
    status: "auto",
    parsed: {
      type: "income",
      amount: 800.00,
      date: "2026-05-19",
      merchantRaw: "Maria Santos",
      paymentHint: "Inter",
    },
    suggestions: { idPaymentSource: "pix", idSource: "src-pix-pessoa", tagIds: [] },
  },
  {
    id: "n4",
    app: "Bradesco",
    channel: "SMS",
    time: "2h",
    received: "16:38",
    text: "BRADESCO: pagamento de boleto efetuado. R$ 234,50 - ENEL DISTRIB SP em 19/05.",
    status: "auto",
    parsed: {
      type: "expense",
      amount: 234.50,
      date: "2026-05-19",
      merchantRaw: "ENEL DISTRIB SP",
      paymentHint: "Bradesco",
    },
    suggestions: { idPaymentSource: "cc", idSource: "src-enel", tagIds: ["tag-contas-casa"] },
  },
  {
    id: "n3",
    app: "Magazine Luiza",
    channel: "SMS",
    time: "ontem",
    received: "ontem",
    text: "Compra aprovada R$ 1.299,00 em 10x de R$ 129,90 no MAGALU SA - Cartão NuBank final 0712.",
    status: "needs-tags",
    parsed: {
      type: "expense",
      amount: 1299.00,
      installments: 10,
      installmentValue: 129.90,
      date: "2026-05-18",
      merchantRaw: "MAGALU SA",
      paymentHint: "Cartão NuBank final 0712",
    },
    suggestions: { idPaymentSource: "nubank", idSource: "src-magalu", tagIds: [] },
    tokens: [
      { t: "Compra aprovada ", role: "type", value: "expense" },
      { t: "R$ 1.299,00", role: "amount", value: 1299.00 },
      { t: " em " },
      { t: "10x de R$ 129,90", role: "installments", value: 10 },
      { t: " no " },
      { t: "MAGALU SA", role: "merchant" },
      { t: " - " },
      { t: "Cartão NuBank final 0712", role: "payment", value: "nubank" },
      { t: "." },
    ],
  },
  {
    id: "n2",
    app: "Banco do Brasil",
    channel: "SMS",
    time: "ontem",
    received: "ontem",
    text: "BB: Transferencia efetuada R$ 200,00 para JOAO P SILVA em 18/05.",
    status: "needs-review",
    parsed: {
      type: null,
      amount: 200.00,
      date: "2026-05-18",
      merchantRaw: "JOAO P SILVA",
      paymentHint: null,
    },
    suggestions: { idPaymentSource: "cc", idSource: null, tagIds: [] },
    tokens: [
      { t: "BB: " },
      { t: "Transferencia efetuada", role: "type" },
      { t: " " },
      { t: "R$ 200,00", role: "amount", value: 200.00 },
      { t: " para " },
      { t: "JOAO P SILVA", role: "merchant" },
      { t: " em " },
      { t: "18/05", role: "date", value: "2026-05-18" },
      { t: "." },
    ],
  },
  {
    id: "n1",
    app: "Banco Bradesco",
    channel: "SMS",
    time: "15/05",
    received: "15/05",
    text: "Credito em conta R$ 6.450,00 - DEP JUDICIAL FOLHA PG ref 05/2026.",
    status: "needs-review",
    parsed: {
      type: null,
      amount: 6450.00,
      date: "2026-05-15",
      merchantRaw: "DEP JUDICIAL FOLHA PG",
      paymentHint: null,
    },
    suggestions: { idPaymentSource: "cc", idSource: "src-salario", tagIds: ["tag-salario"] },
    tokens: [
      { t: "Credito em conta " },
      { t: "R$ 6.450,00", role: "amount", value: 6450.00 },
      { t: " - " },
      { t: "DEP JUDICIAL FOLHA PG", role: "merchant" },
      { t: " ref 05/2026." },
    ],
  },
  // Worst-case: backend couldn't extract anything confidently.
  {
    id: "n0",
    app: "Desconhecido",
    channel: "SMS",
    time: "14/05",
    received: "14/05",
    text: "ATIVIDADE 4521 em sua conta. Saldo apos: 2890,17. Cod. autorizacao 8814-A. Duvidas? 4002-8922.",
    status: "needs-review",
    parsed: {
      type: null,
      amount: null,
      date: null,
      merchantRaw: null,
      paymentHint: null,
    },
    suggestions: { idPaymentSource: null, idSource: null, tagIds: [] },
    tokens: [
      { t: "ATIVIDADE " },
      { t: "4521" },
      { t: " em sua conta. Saldo apos: " },
      { t: "2890,17" },
      { t: ". Cod. autorizacao " },
      { t: "8814-A" },
      { t: ". Duvidas? 4002-8922." },
    ],
  },
];

// ── Already-classified history (for the home list) ─────────────────────────
window.SAMPLE_HISTORY = [
  { id: "h1", date: "2026-05-19", idSource: "src-uber",    idPaymentSource: "nubank", amount: -18.40,  type: "expense", tagIds: ["tag-app-mobil"], status: "PAID" },
  { id: "h2", date: "2026-05-18", idSource: "src-pao",     idPaymentSource: "itau",   amount: -312.07, type: "expense", tagIds: ["tag-supermercado"], status: "PENDING" },
  { id: "h3", date: "2026-05-18", idSource: "src-spotify", idPaymentSource: "nubank", amount: -21.90,  type: "expense", tagIds: ["tag-streaming"], status: "PAID" },
  { id: "h4", date: "2026-05-17", idSource: "src-freela-sz", idPaymentSource: "cc",   amount: 1800.00, type: "income",  tagIds: ["tag-freelance"], status: "PAID" },
  { id: "h5", date: "2026-05-16", idSource: "src-99",      idPaymentSource: "nubank", amount: -14.20,  type: "expense", tagIds: ["tag-app-mobil"], status: "PAID" },
].map(window.legacyTx);

// ── Credit-card faturas (for the Cartões screen) ───────────────────────────
// Open invoices per card: list of purchases, closing/due info, limit usage.
window.SAMPLE_CARDS = [
  {
    id: "nubank", name: "Cartão NuBank", brand: "Mastercard", last4: "0712",
    gradient: "linear-gradient(135deg, oklch(0.52 0.2 300), oklch(0.4 0.16 295))",
    limit: 8000, billDay: 8, dueLabel: "08 jun", closesInDays: 9, status: "aberta",
    items: [
      { name: "iFood",          date: "2026-05-19", amount: 45.90,  tag: "delivery" },
      { name: "Magazine Luiza", date: "2026-05-18", amount: 129.90, tag: null, inst: "1/10" },
      { name: "Uber",           date: "2026-05-16", amount: 14.20,  tag: "app de mobilidade" },
      { name: "Spotify",        date: "2026-05-15", amount: 21.90,  tag: "streaming" },
      { name: "Amazon",         date: "2026-05-12", amount: 189.90, tag: null },
      { name: "Posto Shell",    date: "2026-05-10", amount: 220.00, tag: "combustível" },
    ],
  },
  {
    id: "itau", name: "Cartão Itaú", brand: "Visa Infinite", last4: "3685",
    gradient: "linear-gradient(135deg, oklch(0.34 0.03 50), oklch(0.22 0.02 50))",
    limit: 12000, billDay: 10, dueLabel: "10 jun", closesInDays: 11, status: "aberta",
    items: [
      { name: "Booking.com",     date: "2026-05-09", amount: 980.00, tag: null },
      { name: "Decathlon",       date: "2026-05-14", amount: 459.90, tag: null },
      { name: "Pão de Açúcar",   date: "2026-05-18", amount: 312.07, tag: "supermercado" },
      { name: "IFD*A M GUILHERME", date: "2026-05-16", amount: 153.98, tag: "restaurante" },
    ],
  },
];
window.cardTotal = (c) => (c.items || []).reduce((s, it) => s + it.amount, 0);

// ── Automation summary (for the Home stat) ─────────────────────────────────
// Baseline for the month; `pending` is computed live from the review queue so
// the bar grows as the user teaches.
window.SAMPLE_AUTOMATION = { monthTotal: 30, autoDone: 22 };

