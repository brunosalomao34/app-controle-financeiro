## Índice

- [Proposta do projeto](#proposta-do-projeto)
- [Funcionalidades](#funcionalidades)
- [Requisitos](#requisitos)
- [Como usar o app](#como-usar-o-app)
- [A planilha do Google Sheets](#a-planilha-do-google-sheets)
- [Tutorial de instalação (do zero)](#tutorial-de-instalação-do-zero)
- [Privacidade e termos de uso](#privacidade-e-termos-de-uso)
- [Contribuições](#contribuições)

---

## Proposta do projeto

Esse app nasceu da ideia de ter um controle financeiro pessoal simples, sem mensalidade de assinatura e sem entregar dados financeiros pra empresas terceiras. Em vez de um banco de dados próprio, ele lê e escreve diretamente numa planilha do Google Sheets — ou seja, **os dados continuam sendo seus**, guardados na sua conta Google, e você pode abrir e editar a planilha manualmente a qualquer momento.

O app foi pensado pra uso pessoal (não é distribuído na Play Store) e para ser compilado e instalado diretamente via Android Studio.

---

## Funcionalidades

- **Novo Lançamento** — cadastro de entradas e saídas, com categoria, forma de pagamento, parcelamento e observações.
- **Mensalidades** — valores fixos recorrentes (assinaturas, academia, etc.), lançados automaticamente do mês atual até dezembro, com opção de editar "a partir de qual mês" e de reordenar a lista.
- **Resumo Mensal** — lista de lançamentos de um mês/ano específico, com edição, exclusão e filtro por forma de pagamento, .
- **Resumo Anual** — tabela mês a mês comparando entradas e saídas no ano.
- Login com Google automático.
- Compatível com o crescimento da planilha (suporta até 5.000 linhas de lançamentos por padrão — veja como aumentar isso mais abaixo).

---

## Requisitos

- Uma **Conta Google** (a mesma que vai ser usada tanto na planilha quanto no login do app).
- **Celular Android 8.0 (API 26) ou superior**.
- Para instalar o app: um computador com **Android Studio** (o app não está disponível na Play Store — veja o tutorial completo abaixo).

---

## Como usar o app

### 1. Login

Ao abrir o app pela primeira vez, toque em **"Entrar com Google"** e escolha a conta que tem acesso à planilha. Nas próximas aberturas, o login acontece automaticamente.

### 2. Novo Lançamento

- Preencha **Descrição**, **Categoria** (lista fixa), **Valor Total**, **Forma de Pagamento** e, se for parcelado, a **Qtd. de Parcelas**.
- Escolha o **Mês/Ano** de referência do lançamento.
- **Observações** é opcional.
- Toque em **Salvar Lançamento**.

### 3. Mensalidades

- Toque no bloco **"Nova Mensalidade"** pra abrir o formulário (Nome, Valor Mensal, Forma de Pagamento, Observações).
- Ao salvar, a mensalidade passa a valer do mês atual até dezembro do ano corrente.
- Na lista de **"Mensalidades Ativas"**, use os ícones de seta pra reordenar a posição de cada uma (essa ordem também é usada no Resumo Mensal).
- **Editar**: abre um formulário perguntando a partir de qual mês a mudança deve valer — os meses anteriores ficam registrados com o valor antigo.
- **Excluir**: encerra a mensalidade a partir de agora - os meses já passados continuam no histórico.

### 4. Resumo Mensal

- Escolha o **Mês** e o **Ano**.
- Use o ícone de filtro pra restringir por forma de pagamento.
- Mensalidades aparecem sempre no topo da lista.
- Toque nos ícones de cada item pra **editar** ou **remover** um lançamento.

### 5. Resumo Anual

- Escolha o **Ano** (só aparecem anos que já têm uma aba de Resumo criada na planilha).
- Veja a tabela mês a mês (com o saldo colorido em verde/vermelho) e o saldo total no ano.

---

## A planilha do Google Sheets

### Estrutura

A planilha tem 3 abas fixas:

| Aba                  | Para que serve                                                                                                                                                                                |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Lançamentos**      | Onde o app grava/lê cada lançamento e mensalidade. Colunas D, J, K, L, M, N e O são calculadas por fórmula ou preenchidas automaticamente pelo app — **não edite essas colunas manualmente**. |
| **Listas**           | Lista fixa de categorias (coluna A) e o tipo associado a cada uma — Entrada ou Saída (coluna C). É aqui que você adiciona ou remove categorias, se quiser.                                    |
| **Modelo de Resumo** | Aba-modelo (oculta/de referência) usada pelo script pra gerar automaticamente a aba de resumo de cada ano (ex: "Resumo 2027") — não deve ser apagada nem renomeada.                           |

As abas de resumo por ano (`Resumo 2026`, `Resumo 2027`, etc.) são criadas a partir do menu personalizado que o script adiciona à planilha — veja a seção seguinte.

### Baixar a planilha modelo

O modelo em branco (sem nenhum lançamento) está disponível em:
📎 **[`docs/Controle_Financeiro_Modelo.xlsx`](docs/Controle_Financeiro_Modelo.xlsx)**

> ⚠️ Depois de baixar, você precisa **converter o arquivo pra Google Sheets nativo** antes de usar com o app — a API do Google Sheets não funciona direto com arquivos em formato Excel/Office. No Google Drive: abra o arquivo `.xlsx` → **Arquivo → Salvar como Planilhas Google**.

### Script de criação automática do próximo ano

A aba `Modelo de Resumo` sozinha não gera as abas `Resumo AAAA` — isso é feito por um script (Google Apps Script) que já vem embutido na planilha modelo, mas que também está disponível para cópia em:
📎 **[`docs/ScriptControleFinanceiro.example`](docs/ScriptControleFinanceiro.example)**

**Como funciona:** o script duplica a aba `Modelo de Resumo`, renomeia pra `Resumo AAAA`, e ajusta as 12 datas da tabela (que na aba-modelo usam um ano fictício, `2000`) somando a diferença até o ano desejado. Se nenhuma aba `Resumo AAAA` existir ainda, ele pergunta o ano antes de criar.

**Como instalar/editar o script na sua cópia da planilha:**

1. Na planilha, vá em **Extensões → Apps Script**.
2. Cole o código acima (substituindo o que já estiver lá, se for o caso).
3. Salve (ícone de disquete) e feche a aba do Apps Script.
4. Recarregue a planilha — vai aparecer um novo menu **"ScriptControleFinanceiro"** na barra superior.
5. Use **Criar próximo ano de Resumo** sempre que precisar gerar a aba do ano seguinte.

> 💡 **Opcional — automatizar**: dentro do editor do Apps Script, é possível configurar um **gatilho (Trigger)** baseado em tempo (ex: todo dia 1º de janeiro) pra rodar `criarProximoAnoResumo()` sozinho, sem precisar clicar no menu. Vá em **Gatilhos** (ícone de relógio na lateral esquerda do editor) → **Adicionar gatilho** → função `criarProximoAnoResumo`, evento "Baseado em tempo" → "Gatilhos de temporizador anual" → mês e dia desejados.

### ⚠️ Observação importante — limite de linhas

O app está configurado pra ler/gravar até a **linha 5000** da aba Lançamentos (constante `ULTIMA_LINHA` em `SheetsRepository.kt`). Se um dia você precisar de mais espaço, são **2 ajustes obrigatórios** (fazer só um dos dois não resolve):

1. **No código do app** — em `app/src/main/java/com/pessoal/controlefinanceiro/data/SheetsRepository.kt`, aumente o valor de:
   ```kotlin
   private const val ULTIMA_LINHA = 5000
   ```
2. **Na planilha** — as fórmulas `ARRAYFORMULA` das colunas D, I, J, K, L, M da aba Lançamentos (linha 3) também terminam em `$5000` — é preciso trocar esse número pra bater com o novo limite. O jeito mais rápido é usar **Localizar e substituir** (Ctrl+H) na aba Lançamentos, marcando "Pesquisar também nas fórmulas", trocando `$5000` pelo novo valor.

Se os dois números (código e planilha) ficarem diferentes, linhas além do menor dos dois limites simplesmente não vão aparecer nos resumos, mesmo que o app consiga gravar nelas — então sempre ajuste os dois juntos.

---

## Tutorial de instalação (do zero)

Esse tutorial parte do princípio de que você **não tem nenhuma conta/projeto configurado ainda**. Se algum passo já estiver feito, pule pro próximo.

### Parte 1 — Preparar a planilha

1. Baixe o modelo em [`docs/Controle_Financeiro_Modelo.xlsx`](docs/Controle_Financeiro_Modelo.xlsx) e suba no seu Google Drive.
2. Faça upload da planilha em seu Google Drive e em seguida abrir para converter em Google Sheets: **Arquivo → Salvar como Planilhas Google**.
3. Copie o **ID da planilha** — é o trecho da URL entre `/d/` e `/edit`:
   ```
   https://docs.google.com/spreadsheets/d/ESSE_TRECHO_AQUI_É_O_ID/edit
   ```
4. Instale o script de criação de anos (veja a seção [Script de criação automática do próximo ano](#script-de-criação-automática-do-próximo-ano) acima) e rode **Criar próximo ano de Resumo** pelo menos uma vez, pra já existir a aba `Resumo` do ano atual.

### Parte 2 — Google Cloud Console

1. Acesse [console.cloud.google.com](https://console.cloud.google.com/) e crie um novo projeto (ex: `Controle Financeiro App`).
2. Em **APIs e Serviços → Biblioteca**, ative a **Google Sheets API** e a **Google Drive API**.
3. Em **APIs e Serviços → Tela de permissão OAuth**:
   - Preencha nome do app e seus e-mails de contato/suporte.
   - Tipo de usuário: **Externo**.
   - Em **Acesso a dados > Escopos**, adicione `.../auth/spreadsheets` e `.../auth/drive.file`.
   - Em **Usuários de teste**, adicione o seu próprio e-mail Google (enquanto o app não é verificado pelo Google, só esses e-mails conseguem fazer login).
4. Em **APIs e Serviços → Credenciais**, crie duas credenciais **ID do cliente OAuth**:
   - Tipo **Android**: vai pedir o **nome** (`Controle Financeiro - Android Debug`) e **nome do pacote** (`com.pessoal.controlefinanceiro`) e a **impressão digital SHA-1** — você só consegue gerar o SHA-1 depois de ter o projeto Android Studio aberto (Parte 3, passo 4), então pode voltar aqui depois.
   - Tipo **Aplicativo da Web**: não precisa preencher nada além do **nome** (`Controle Financeiro - Web Client`)
   - Depois de criar, **copie o Client ID** gerado (algo como `123456789-abc...apps.googleusercontent.com`), ele vai ser usado no código.

### Parte 3 — Preparar o Android Studio

1. Instale o [Android Studio](https://developer.android.com/studio).
2. Clone este repositório:
   ```
   git clone https://github.com/brunosalomao34/app-controle-financeiro.git
   ```
3. Abra o projeto no Android Studio e deixe o Gradle sincronizar.
4. Gere o SHA-1 de debug: abra o **Terminal** dentro do Android Studio e rode:
   ```
   ./gradlew signingReport
   ```
   (No Windows: `.\gradlew signingReport`.) Copie o valor de `SHA1` da variante **debug**.
5. Volte no Google Cloud Console (Parte 2, passo 4) e complete a credencial **Android** com esse SHA-1.

### Parte 4 — Configurar o projeto localmente

1. Na raiz do projeto (mesmo nível do `settings.gradle.kts`), crie/edite o arquivo **`local.properties`** (esse arquivo nunca vai pro Git — é onde ficam seus dados sensíveis):
   ```properties
   sdk.dir=/caminho/para/seu/Android/sdk
   SPREADSHEET_ID=cole_aqui_o_id_da_sua_planilha
   ```
2. No código, o **Client ID do tipo Web** que você copiou na Parte 2 precisa estar referenciado onde o app faz a solicitação de escopo do Google Sign-In — confira o arquivo `ui/login/LoginActivity.kt`.

### Parte 5 — Instalar no celular

1. Ative o **Modo desenvolvedor** e a **Depuração USB** no seu celular Android.
2. Conecte o celular ao computador via USB (autorize a depuração quando o celular perguntar).
3. No Android Studio, com o celular selecionado como dispositivo, clique em **Run ▶**.
4. O app instala e abre automaticamente. Faça login com a mesma conta Google usada na planilha.

> Alternativa sem cabo USB: gere um APK (**Build → Build Bundle(s) / APK(s) → Build APK(s)**), transfira o arquivo `.apk` pro celular por qualquer meio (Drive, e-mail, etc.) e instale manualmente (é preciso permitir "instalar apps de fontes desconhecidas" no Android).

---

## Privacidade e termos de uso

Esse app **não é distribuído pela Play Store** e não é um produto comercial — é um projeto pessoal, de uso individual, disponibilizado aqui como código aberto. Por isso:

- **Não é obrigatório** ter uma Política de Privacidade ou Termos de Uso formais publicados, já que o Google só exige isso para apps publicados/verificados publicamente (Play Store ou apps OAuth "Em produção" acessados por terceiros). Enquanto a tela de consentimento OAuth estiver em modo **Teste**, com usuários de teste cadastrados manualmente, essa exigência não se aplica.
- Ainda assim, como o código está público no GitHub, vale deixar claro pra quem for usar o projeto:
  - O app só acessa a planilha do Google Sheets vinculada à conta que faz login — nenhum dado é enviado a servidores próprios ou de terceiros além da própria API do Google.
  - Todo o controle sobre os dados (edição, exclusão, backup) fica inteiramente na planilha do usuário.

---

## Contribuições

Se você usar o projeto e tiver sugestões, encontrar bugs ou quiser propor melhorias, fique à vontade pra abrir uma issue ou um pull request 🙂
