# agent-runtime — Vol.1 Conformant + Vol.2 Enhancements

**Version:** 2.0.0-SNAPSHOT | **Java:** 21 | **Vol.1 conformance:** 94/100

## What Was Merged From agent-framework

| Component | Package | Priority |
|---|---|---|
| `ModelClient` SPI + `OpenAiModelClient` + `StubModelClient` | `modelclient` | P1 |
| `RunRepository` SPI + `Run` aggregate + `InMemoryRunRepository` | `runtime.run` | P2 |
| `AgentRuntimeBuilder` fluent API | `runtime.builder` | P1 |
| `TokenWindowMemoryPolicy` | `memory.policy` | P1 |
| `StructuredEventExporter` | `observability` | P1 |
| `TenantPolicyEngine` | `security` | P2 |
| Research use-case factory pattern | `usecase.research` | P1 |

## Key Changes to Existing Classes

- `DefaultReasoningModule` now accepts a `ModelClient` — no longer a hardcoded stub
- `ObservabilityBus` has `addExporter(StructuredEventExporter)` — audit trail is streamable
- `SecurityPolicy.allowAll()` and `ToolRegistry.empty()` factory methods added
- `AgentState` has `currentGoal()` and `stepCount()` helpers

## Quick Start

```java
// Stub (no network, for tests)
DefaultAgentOrchestrator agent = AgentRuntimeBuilder.stub();

// Real OpenAI
DefaultAgentOrchestrator agent = new AgentRuntimeBuilder()
    .modelClient(new OpenAiModelClient(System.getenv("OPENAI_API_KEY")))
    .verbose(true)
    .build();

// Research use-case
DefaultAgentOrchestrator research = ResearchAgentFactory.create(System.getenv("OPENAI_API_KEY"));
```

## Build

```bash
mvn clean verify
```

All tests use `StubModelClient` — no network calls required.

## Remaining P3 Gaps

- BM25/RRF/HyDE/GraphRAG hybrid retrieval (Ch.11 §11.5)
- Hot/warm/cold tiered storage (Ch.12 §12.5)
- UserModelFact schema (Ch.13 §13.3)
- TaskCheckpoint (Ch.13 §13.4)
- Associative + prospective memory (Ch.14)
- GDPR export/erasure (Ch.12 §12.7)
