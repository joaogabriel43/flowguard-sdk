# FlowGuard SDK — CLAUDE.md

**Nome:** FlowGuard SDK
**Versão:** 1.1.0
**Data:** 2026-05-18

---

## Visão Geral

SDK Java para integração com o FlowGuard Server. Permite que aplicações consumidoras avaliem feature flags localmente, com zero latência por request, cache em memória e atualização em tempo real via SSE (Server-Sent Events).

**Stack:**

- Java 21
- Maven (gerenciamento de dependências e build)
- OkHttp (comunicação HTTP e listener SSE)
- Jackson (serialização/desserialização JSON)

**Características principais:**

- **Avaliação local de flags:** todas as flags são mantidas em memória; a avaliação ocorre no cliente, sem round-trip ao servidor por request.
- **Fallback automático:** se o servidor estiver indisponível, o SDK usa o último estado conhecido em memória, garantindo resiliência.
- **Atualizações em tempo real via SSE:** o SDK mantém uma conexão SSE com o servidor e atualiza o cache local imediatamente ao receber notificações de mudança.
- **Publicação no GitHub Packages:** distribuído como dependência Maven (`com.flowguard:flowguard-sdk`) via GitHub Packages.

---

## Estrutura de Pastas Esperada

```
src/main/java/com/flowguard/sdk/
├── core/          # Lógica de avaliação de flags, hashing MurmurHash3, modelos de domínio
├── client/        # Comunicação HTTP com o servidor, listener SSE, reconexão automática
├── cache/         # Cache local em memória com suporte a fallback
├── config/        # Auto-configuration Spring Boot (opcional, não obrigatória no core)
└── exception/     # Exceções tipadas do SDK
```

---

## ADRs Já Fechados

### ADR-001: Repositório Separado do Server
Ciclo de vida e versionamento independentes. O SDK evolui em ritmo próprio, sem acoplamento ao calendário de releases do servidor. Times consumidores podem travar uma versão do SDK sem impactar o servidor.

### ADR-002: Avaliação Local no SDK
Flags são carregadas em memória durante a inicialização do SDK. Toda avaliação ocorre localmente — zero latência por request, sem dependência de rede no caminho crítico.

### ADR-003: SSE para Receber Push do Servidor
O SDK mantém um listener SSE reativo contra o FlowGuard Server. Toda mudança de flag é empurrada pelo servidor e aplicada imediatamente no cache local. Reconexão automática com backoff exponencial em caso de queda.

### ADR-004: Fallback Local
Se o servidor cair ou a conexão SSE for interrompida, o SDK continua operando com o último estado conhecido em memória. O fallback padrão (retornar `true` ou `false` para flags desconhecidas) é configurável pelo consumidor.

### ADR-005: MurmurHash3 para Rollout
Mesma implementação de hashing utilizada no FlowGuard Server. Garante consistência de bucketing: o mesmo `userId` sempre cai no mesmo percentual, independentemente de qual lado (servidor ou SDK) realiza o cálculo.

### ADR-006: Publicação via GitHub Packages
O artifact é publicado como `com.flowguard:flowguard-sdk` no GitHub Packages. Consumidores adicionam o repositório GitHub Packages ao seu `pom.xml` e declaram a dependência normalmente via Maven.

### ADR-007: Spring Boot Auto-configuration
O SDK oferece auto-configuração opcional para projetos Spring Boot, permitindo configuração via `application.properties` (ex: `flowguard.server-url`, `flowguard.api-key`, `flowguard.default-fallback`). O módulo `core` não tem dependência do Spring — a auto-configuração é um módulo separado e opcional.

---

## Padrões do Projeto

- **Zero-opinião sobre o consumidor:** o SDK não impõe frameworks, arquiteturas ou padrões ao projeto que o consome. Funciona em qualquer aplicação Java 21+.
- **API pública mínima:**
  - `flowGuard.isEnabled(flagKey, userId)` — avaliação simples por flag e usuário.
  - `flowGuard.isEnabled(flagKey, userId, attributes)` — avaliação com atributos adicionais para segmentação.
- **Core sem Spring:** nenhuma dependência do Spring Framework é obrigatória no módulo `core`. A auto-configuração Spring Boot existe como camada adicional e opcional.
- **Código-fonte em inglês:** todo código, comentários de código, nomes de classes, pacotes e métodos devem ser escritos estritamente em inglês. Documentação e explicações externas (como este arquivo) permanecem em português.
- **Commits semânticos:** `feat:`, `fix:`, `test:`, `docs:`, `refactor:`.
- **Versionamento semântico estrito:** `MAJOR.MINOR.PATCH` — quebra de compatibilidade de API pública eleva MAJOR obrigatoriamente.

---

## Regras de Negócio

- O SDK **nunca** avalia uma flag fazendo chamada ao servidor — o processamento é sempre local, a partir do cache em memória.
- O **fallback padrão** (valor retornado quando uma flag não é encontrada) é configurável pelo consumidor: `true` ou `false`. O padrão de fábrica é `false`.
- A **reconexão SSE** é automática, implementada com backoff exponencial (ex: 1s → 2s → 4s → ... até um teto configurável) para evitar sobrecarga no servidor em cenários de instabilidade.
- O **cache local nunca expira por tempo** — ele é atualizado exclusivamente via push do servidor (SSE) ou no restart da aplicação consumidora. Não há TTL baseado em tempo.

---

## Erros Conhecidos e Como Evitá-los

### [2026-05-18] Erro: Janela de cache vazio em clear()+putAll() no loadFlags()
**O que aconteceu:** `cache.clear()` e `cache.putAll()` eram chamadas `synchronized` separadas. Entre elas, `cache.get()` (não sincronizado) via uma thread concorrente retornava `null` para todas as flags, ativando o fallback falsamente.
**Por que:** Duas chamadas ao monitor de `FlagCache` com uma lacuna entre elas; `get()` usa apenas o lock interno do `ConcurrentHashMap`, não o monitor do objeto.
**Como prevenir:** Sempre usar `cache.replace(flagMap)` para substituição atômica — nunca `clear()` seguido de `putAll()` em chamadas separadas.

### [2026-05-18] Erro: toggle() e replace() usavam locks incompatíveis no FlagCache
**O que aconteceu:** `replace()` era `synchronized` em `FlagCache.this`, mas `toggle()` usava apenas `ConcurrentHashMap.computeIfPresent()`. Um evento de toggle durante um snapshot causava descarte silencioso do toggle.
**Por que:** Dois níveis de locking incompatíveis (monitor do objeto vs. lock interno do CHM). Se `replace()` executar `clear()` antes do `toggle()`, a chave some e `computeIfPresent` não faz nada.
**Como prevenir:** Todos os métodos de escrita no `FlagCache` devem ser `synchronized` no mesmo monitor. Não misturar `synchronized(this)` com operações atômicas isoladas do CHM em métodos de escrita.

### [2026-05-18] Erro: Campo `attempt` não-volatile em multi-thread no FlagSseListener
**O que aconteceu:** `int attempt` era lido em `connectLoop` (executor thread) e escrito em `onOpen` (OkHttp callback thread) sem garantia de visibilidade entre threads.
**Por que:** Sem `volatile`, o Java Memory Model não garante que a escrita de uma thread seja visível para outra. `attempt++` no connectLoop também não é atômico.
**Como prevenir:** Usar `AtomicInteger` para campos numéricos compartilhados entre threads distintas. `volatile int` é suficiente apenas se uma única thread escreve; com duas threads escrevendo, `AtomicInteger` é obrigatório.

### [2026-05-18] Erro: processUpdated() bloqueava a thread de callback do OkHttp SSE
**O que aconteceu:** Chamadas HTTP síncronas (`client.fetchSingleFlag()`, `client.loadFlags()`) dentro de `processUpdated()` bloqueavam a thread de callback do OkHttp. Durante o bloqueio, nenhum outro evento SSE era processado.
**Por que:** Callbacks SSE do OkHttp rodam em uma thread do dispatcher. Qualquer IO síncrono nessa thread atrasa ou descarta eventos subsequentes.
**Como prevenir:** Qualquer operação de IO dentro de callbacks SSE deve ser delegada a um executor dedicado. No SDK: `cacheUpdateExecutor` (thread `flowguard-cache-updater`) recebe as tarefas de hidratação HTTP; a callback retorna imediatamente.

### [2026-05-18] Erro: Dependência circular writer/latch no teste de concorrência
**O que aconteceu:** `finishLatch` era `readerCount + 1` (incluía o writer). O writer só fazia countdown após `running = false`, mas `running = false` só era setado após `await()` do latch. O teste sempre sofria timeout de 5 segundos silenciosamente.
**Por que:** Dependência circular: `await` esperava o writer → writer esperava `running=false` → `running=false` só vinha após `await`. O `boolean finished` era ignorado, escondendo o timeout.
**Como prevenir:** Em testes com thread writer de duração indefinida, nunca incluir o writer no `CountDownLatch`. O latch deve cobrir apenas workers com término previsível. O writer é parado de forma independente por um `AtomicBoolean`. O retorno de `await()` deve ser sempre assertado com `assertTrue(finished)`.

---

## Otimizações e Performance

### [2026-05-18] Substituição atômica do cache via replace() em vez de clear()+putAll()
**Contexto:** Toda carga de flags (`loadFlags()`, `processSnapshot()`) precisava substituir o mapa inteiro. A abordagem anterior usava `clear()` + `putAll()` separados.
**Solução:** `FlagCache.replace(Map)` executa ambas as operações em um único bloco `synchronized`, eliminando a janela de inconsistência. Chamadas de `get()` nunca veem o cache vazio entre as duas etapas.
**Resultado:** Zero falsos fallbacks durante recargas de cache — impacto direto em aplicações com alto throughput de chamadas a `isEnabled()`.

### [2026-05-18] Executor dedicado para hidratações HTTP em eventos SSE
**Contexto:** Eventos `flag-updated` slim disparavam `client.fetchSingleFlag()` síncronos dentro da callback do OkHttp.
**Solução:** Thread `flowguard-cache-updater` (executor single-thread) recebe a tarefa; a callback SSE retorna imediatamente.
**Resultado:** Eventos SSE em rajada não se acumulam mais durante hidratações de flag individual.

---

## Padrões de Thread Safety do Projeto

- **Regra do monitor único:** todos os métodos de escrita em `FlagCache` devem usar `synchronized` no mesmo monitor (`this`). Nunca misturar `synchronized(this)` com operações atômicas isoladas do `ConcurrentHashMap` para métodos de escrita.
- **AtomicInteger para contadores compartilhados entre threads:** campos numéricos escritos de mais de uma thread usam `AtomicInteger`, não `int` ou `volatile int`.
- **IO em callbacks SSE sempre delegado:** qualquer chamada de rede dentro de um `EventSourceListener` deve ser submetida a um executor separado, nunca executada na callback diretamente.
- **CountDownLatch em testes cobre apenas workers com término previsível:** writers de duração indefinida são parados por `AtomicBoolean` independente do latch; `assertTrue(latch.await(...))` é obrigatório.

---

## Contrato da API Pública

- `isEnabled(flagKey, userId)` e `isEnabled(flagKey, userId, attributes)` **nunca lançam exceção** para o consumidor — erros retornam `fallbackStrategy.evaluate()`.
- `flagKey == null` ou vazio → fallback + `logger.warn`.
- `userId == null` → fallback + `logger.warn` (introduzido em v1.1.0; antes causava hash silencioso de `"flagkeynull"`).
- Consumidores que passam `userId` de contextos que podem ser nulos (ex: usuário não autenticado) devem tratar isso antes de chamar `isEnabled()`, ou configurar `defaultFallback = true` para esses casos.

---

## Agentes: Casos de Uso Confirmados

| Agente | Tarefa | Resultado |
|--------|--------|-----------|
| `engineering-code-reviewer` | Revisão crítica pré-portfólio (thread safety, resiliência, API, CI, testes, publicação) | Gerou relatório com 15 issues categorizados por severidade |
| `engineering-senior-developer` | Implementação de 12 correções em 4 commits semânticos | Todos os itens do relatório implementados sem regressão |

---

## Changelog do CLAUDE.md

| Versão | Data       | Descrição                                                                                                   | Autor     |
|--------|------------|-------------------------------------------------------------------------------------------------------------|-----------|
| 1.0.0  | 2025-05-17 | Criação inicial com visão, ADRs e padrões do SDK                                                            | @engineering-backend-architect |
| 1.1.0  | 2026-05-18 | Revisão crítica pré-portfólio: 5 erros conhecidos, 2 otimizações, padrões de thread safety, contrato de API pública, casos de uso de agentes confirmados | @engineering-code-reviewer + @engineering-senior-developer |
