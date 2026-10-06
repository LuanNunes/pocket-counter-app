# Protótipo iOS 26 — especificação de design

Fonte de verdade da UI do app iOS. **Não é código do app e não roda como está**: é um
protótipo React/JSX sem bundler, e o HTML host que o carregava não veio no bundle (não
existe nenhum `.html` neste repositório). Leia-o como spec; não tente executá-lo nem
portá-lo linha a linha.

O app iOS vive em `../../ios/` e é Swift/SwiftUI nativo. Nada daqui é compilado.

## Como consumir

| quero saber | onde olhar |
|---|---|
| o que uma tela mostra e como se comporta | o `.jsx` da tela (lista abaixo) |
| tokens de cor (light e dark) | `glass.css`, linhas 11-15 |
| margens, raios, tamanhos de controle | `screens.css`, por seção comentada |
| o shape dos dados que a tela espera | `data.js` |
| navegação e estado global | o router no topo de `app.jsx` |

`glass.css` usa OKLCH. `SwiftUI.Color` não tem inicializador OKLCH — os valores são
convertidos para sRGB uma única vez e gravados como Color Sets no asset catalog. Não
ajuste as cores à mão no Swift: regenere a partir destes tokens se a spec mudar.

As classes `.glass` existem aqui só porque CSS não tem Liquid Glass. No iOS 26 elas são
`.glassEffect()` e componentes nativos — não reimplemente o blur.

## Arquivos

| arquivo | conteúdo |
|---|---|
| `app.jsx` | entrada e router: `view` (lock/app), `tab` (inicio/transacoes/cartoes/mais), `sub` (report/tags/atalhos) |
| `home.jsx` | Início — hero de saldo, KPIs, banner de revisão, tiles |
| `tx.jsx` | Transações — Despesas/Receitas, agrupar por Lista/Categoria/Tag, modo Editar |
| `report.jsx` | Relatório — visão geral por categoria/fonte e Por tag (gráfico mensal, mapa de calor) |
| `more.jsx` | Mais, Categorias & Tags, Cartões, Config. de lançamento rápido |
| `quickadd.jsx` | Lançamento rápido em linguagem natural |
| `system.jsx` | Tela Bloqueada (widgets + Live Activity), Siri (App Intent), Action Button → Dynamic Island |
| `chrome.jsx` | chrome iOS 26 — device frame, status bar, Dynamic Island, barras Liquid Glass, sheets, menus, ícones |
| `store.jsx` | store em memória do protótipo |
| `data.js` | dados de amostra; o shape espelha os DTOs do backend |
| `nl-parse.js` | parser pt-BR do lançamento rápido |
| `glass.css` | tokens + primitivas de Liquid Glass |
| `screens.css` | layout por tela |
| `tweaks-panel.jsx` | **harness de prototipagem, não faz parte do app** — `@ds-adherence-ignore` |

## Fora do escopo do protótipo

"Contas fixas" e "Regras aprendidas" existem no Android e foram deliberadamente deixadas
de fora daqui (`app.jsx` emite o toast "Igual ao Android — fora deste escopo").

Duas afirmações nos cabeçalhos **não** correspondem ao repositório e não devem ser
tomadas como contrato:

* `nl-parse.js` diz ser "usado pelo app web e pelo app Android". Não existe equivalente no
  código Android — esta é a única cópia no repo.
* o banner "N lançamentos para revisar" da Início depende da captura de notificações do
  Android (`NotificationListenerService`). O iOS não tem equivalente, então o banner não é
  implementável como mostrado.

## Se você precisar vê-lo rodando

Falta um HTML host que carregue React + Babel por CDN e os arquivos nesta ordem —
dependências primeiro, `app.jsx` por último, porque ele é quem chama `ReactDOM.createRoot`:

```
glass.css, screens.css
data.js, nl-parse.js, store.jsx, chrome.jsx
home.jsx, tx.jsx, report.jsx, more.jsx, quickadd.jsx, system.jsx, tweaks-panel.jsx
app.jsx
```

Esse host não está versionado e não é necessário para implementar o app.
