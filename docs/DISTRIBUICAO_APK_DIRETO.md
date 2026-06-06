# Tutorial: Distribuição de APK via Link Direto (Sem Google Play)

## Parte 1 — Gerar APK Assinado no Android Studio

### Passo 1: Abrir o projeto no Android Studio

1. Abra Android Studio
2. Abra o projeto `neapp/` (ou crie/abra seu projeto Android)

### Passo 2: Preparar o Build Release

Menu → **Build** → **Generate Signed Bundle / APK...**

Vai abrir um diálogo:

```
┌─────────────────────────────────────┐
│ Generate Signed Bundle or APK       │
├─────────────────────────────────────┤
│ ⦿ APK                               │ ← Selecione APK
│ ○ Android App Bundle                │
└─────────────────────────────────────┘
```

Clique **Next**.

### Passo 3: Criar ou Selecionar Keystore

**Se é a primeira vez:**

1. Clique **Create new...**
2. Preencha:
   - **Key store path:** `/caminho/para/meu_app.jks` (salve em local seguro, você vai precisar disso sempre)
   - **Password:** senha forte (ex: `MyApp@2025!Secure`)
   - **Confirm:** repita a senha
   - **Key alias:** `meu_app_key` (nome da chave)
   - **Key password:** mesma senha ou diferente
   - **Validity (years):** 25 (válido por 25 anos)
   - Nome, sobrenome, etc.: opcional

Clique **OK**.

**Se já tem keystore:**

1. Clique **Choose existing...**
2. Selecione o arquivo `.jks`
3. Insira a senha do keystore
4. Selecione o alias da chave
5. Insira a senha da chave

Clique **Next**.

### Passo 4: Selecionar Build Variants

```
┌──────────────────────────┐
│ Build Variants           │
├──────────────────────────┤
│ Release  ← Selecione     │
│ Debug                    │
└──────────────────────────┘
```

- **Build type:** `release`
- **Flavors:** deixe em branco (ou selecione se tiver sabores)

Marque **V1 (Jar Signature)** e **V2 (Full APK Signature)** (ambas selecionadas por padrão).

Clique **Finish**.

### Passo 5: Android Studio constrói o APK

Aguarde. Verá na tela:

```
Build → Executing tasks: 100%
✓ Build APK completed successfully
```

Um **pop-up** aparece:

```
✓ APK(s) generated successfully for module 'app' with 4 flavors.

[Locate] [Analyze APK] [Open For Sending]
```

Clique **Locate** para ir para a pasta onde o APK foi salvo.

### Passo 6: Localizar o APK

Normalmente fica em:

```
neapp/app/release/app-release.apk
```

(ou `neapp-release.apk` dependendo do nome do projeto)

**Copie o arquivo para um local acessível** (Desktop, pasta de downloads, etc).

---

## Parte 2 — Hospedar o APK Online

### Opção A: Google Drive (Mais fácil para testes)

1. Acesse [drive.google.com](https://drive.google.com)
2. Faça login com sua conta Google
3. Crie uma pasta: **Clique direito** → **New folder** → nomeie como `"APK_Testes"`
4. Arraste o APK (`app-release.apk`) para dentro da pasta
5. **Clique direito no arquivo** → **Share**
6. Altere para **"Anyone with the link"** → **Viewer**
7. Copie o link

**Link fica assim:**
```
https://drive.google.com/file/d/1aB2cDeFgHijKlMnOpQrStUvWxYz/view?usp=share_link
```

**Encurte o link** (opcional) usando [bit.ly](https://bitly.com) ou [tinyurl.com]:

1. Cole a URL longa
2. Gera URL curta: `https://bit.ly/3abc1234`

---

### Opção B: Servidor/VPS (Se você já tem uma)

1. **SSH para a VPS:**
   ```bash
   ssh user@seu-vps.com
   ```

2. **Criar pasta de downloads:**
   ```bash
   mkdir -p /var/www/apks
   ```

3. **Copiar APK via SCP (do seu Mac):**
   ```bash
   scp ~/Desktop/app-release.apk user@seu-vps.com:/var/www/apks/
   ```

4. **Configurar permissões:**
   ```bash
   chmod 644 /var/www/apks/app-release.apk
   ```

5. **Link fica:**
   ```
   https://seu-vps.com/apks/app-release.apk
   ```

---

### Opção C: GitHub Releases

1. Crie um repositório no GitHub (ou use um existente)
2. Vá para **Releases** → **Create a new release**
3. Clique **Attach binaries**
4. Arraste o `app-release.apk`
5. Clique **Publish release**
6. O link do APK fica:
   ```
   https://github.com/seu-usuario/seu-repo/releases/download/v1.0/app-release.apk
   ```

---

## Parte 3 — Instruções para Instalação nos Celulares de Teste

### Para cada testador, envie esse passo a passo:

---

## 📱 COMO INSTALAR O APK DE TESTE

### ⚠️ Aviso importante

O app **não está na Google Play Store** — você vai instalar direto do arquivo APK. Isso é seguro se o link for confiável.

### Passo 1: Permitir instalação de fontes desconhecidas

1. Abra **Configurações** do Android
2. Vá para **Segurança** (ou **Privacidade e segurança**)
3. Procure por **Fontes desconhecidas** ou **Instalar apps desconhecidos**
4. Encontre o navegador que você vai usar (Chrome, Firefox, etc)
5. **Marque a caixa** ao lado dele

**Exemplo (pode variar por telefone):**
```
Configurações
 ↓
Segurança
 ↓
Fontes desconhecidas
 ↓
Chrome ☑️ (Permitir instalação de apps desconhecidos)
```

### Passo 2: Baixar o APK

1. **No seu celular**, abra o navegador (Chrome, Firefox, etc)
2. Cole o link que você recebeu:
   ```
   https://link-do-apk-aqui
   ```
3. Espere o APK baixar (pode levar alguns segundos)
4. Uma mensagem aparece:
   ```
   "app-release.apk
    Baixado com sucesso"
   ```

### Passo 3: Instalar o APK

**Método A — Automático (recomendado):**

1. Logo após baixar, um pop-up aparece: **"Instalar"**
2. Clique em **Instalar**
3. Aguarde (pode levar 10-30 segundos)
4. Mensagem: **"Instalado com sucesso"**
5. Clique em **Abrir** (ou minimize para ver o app na home)

**Método B — Manual:**

1. Abra o **Gerenciador de Arquivos** (ou **Arquivos**)
2. Vá para **Downloads**
3. Procure por `app-release.apk`
4. **Toque** no arquivo
5. Clique **Instalar**
6. Aguarde a instalação

### Passo 4: Confirmar a instalação

1. Vá para a **Home** do seu celular
2. Procure pelo app novo (ex: **"NeApp Tracker"**)
3. Clique para abrir
4. ✅ **Pronto! App instalado**

---

## Troubleshooting — Problemas na Instalação

### "Não é possível instalar a partir de fonte desconhecida"

**Solução:**
1. Vá para **Configurações**
2. **Segurança**
3. Habilite **Fontes desconhecidas**
4. Tente novamente

### "Aplicação bloqueada por Play Protect"

**Solução:**
1. Abra **Google Play Protect** (app Google Play Store)
2. Clique no seu avatar → **Play Protect**
3. Desative **"Escanear apps com Play Protect"** (temporariamente)
4. Tente instalar de novo

### "Versão do Android não suportada"

**Verificação:**
1. Seu telefone precisa estar em **Android 8.0 ou superior**
2. Vá para **Configurações** → **Sobre o telefone** → **Versão do Android**
3. Se for menor que 8.0, o app não rodará

### O app instalou mas não abre

**Solução:**
1. Desinstale: **Configurações** → **Aplicativos** → **NeApp Tracker** → **Desinstalar**
2. Baixe e instale novamente
3. Se persistir, avise ao desenvolvedor

---

## 🔄 Atualizar o App (Nova Versão)

Quando tiver uma nova versão do APK:

1. Repita o **Passo 2** acima (baixar novo APK)
2. O Android detectará que é atualização do mesmo app
3. Clique **Atualizar** (em vez de "Instalar")
4. Pronto! Versão antiga será substituída

---

## 📧 Template para enviar aos testadores

Copie e envie via WhatsApp, email, ou mensagem:

---

> **Link para baixar o app de teste:**
>
> 📥 [Cole o link do APK aqui]
>
> **Como instalar (passo a passo):**
> 1. Baixe no celular usando o link acima
> 2. Vá para Configurações → Segurança → Habilite "Fontes desconhecidas"
> 3. Abra o arquivo baixado (app-release.apk)
> 4. Clique "Instalar"
> 5. Pronto!
>
> **Versão:** 1.0
> **Data:** 26/05/2026
>
> Qualquer erro, me avisa no WhatsApp!

---

## 📋 Checklist final

Antes de distribuir:

- [ ] APK gerado e testado no seu Android
- [ ] Link funciona (tente baixar em outro navegador)
- [ ] Testadores têm acesso ao link
- [ ] Testadores receberam instruções de instalação
- [ ] Você sabe como coletar feedback (email, formulário, etc)
