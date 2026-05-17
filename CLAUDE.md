# FlowGuard SDK — CLAUDE.md

**Nome:** FlowGuard SDK
**Versão:** 1.0.0
**Data:** 2025-05-17

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

> _Seção a ser preenchida progressivamente conforme o projeto avança._

---

## Otimizações e Performance

> _Seção a ser preenchida progressivamente conforme o projeto avança._

---

## Agentes: Casos de Uso Confirmados

> _Seção a ser preenchida progressivamente conforme o projeto avança._

---

## Changelog do CLAUDE.md

> _Seção a ser preenchida progressivamente conforme o projeto avança._

| Versão | Data       | Descrição                                      | Autor     |
|--------|------------|------------------------------------------------|-----------|
| 1.0.0  | 2025-05-17 | Criação inicial com visão, ADRs e padrões do SDK | @engineering-backend-architect |
