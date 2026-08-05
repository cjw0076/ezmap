# EZ Adaptive Production System Specification v1

Status: Proposed
Owner: TeamEZ
Target branch: `next/mobile-agent-fabric`
Target product: `EZ Adaptive`

> Phone decides. Nodes execute. Evidence returns.

This document defines the production system that replaces the current single-module mobility demo without discarding its useful agent, skill, MCP, Room, voice, and Android integration code.

---

## 1. Product Definition

EZ Adaptive is a phone-owned personal computing fabric. The Android phone is the root authority for identity, personal memory, policy, approval, canonical execution history, and receipt verification. Windows and Linux/GPU machines are replaceable execution nodes. A cloud relay is an opaque transport fallback, not the brain or source of truth.

The system must continue working when:

- the model provider changes;
- a desktop is offline or replaced;
- direct Tailscale connectivity fails;
- an Android process is killed;
- a request is delivered more than once;
- a node reports success incorrectly;
- an untrusted document or notification contains prompt injection;
- the network is unavailable for hours;
- a model pack is rolled back independently of the APK.

The system is not production-ready until all of those conditions are tested.

---

## 2. Non-Negotiable Invariants

1. Models propose actions; they never obtain actuator authority.
2. Every actuator is a typed, versioned capability.
3. Every effectful capability implements `prepare`, `authorize`, `commit`, and `verify`.
4. `prepare` must be side-effect free.
5. User approval is bound to the exact prepared digest and expires.
6. `commit` is journaled and idempotent.
7. Success is determined by a postcondition verifier, not model text or absence of exceptions.
8. External content can provide observations but cannot create effectful goals or grants.
9. Learning may improve ranking, timing, and routing but cannot widen capability scope.
10. Core, Power, and Managed editions have separate packages, manifests, signing keys, and distribution policies.
11. The phone owns the canonical event ledger. Nodes return proposals, observations, artifacts, and receipts.
12. Unknown protocol fields are preserved where possible; unknown effectful capability versions fail closed.

---

## 3. Production Trust Zones

### Zone A — Android Personal Authority

Owns:

- primary device identity;
- personal memory authority;
- policy ledger;
- approval UI;
- thread and run state;
- canonical event journal;
- node and model-pack revocation;
- receipt verification;
- local context minimization and redaction.

This zone is allowed to deny any action. It is not allowed to silently broaden an existing grant.

### Zone B — Core Android Capability Runtime

Contains Play-compatible, narrowly scoped capabilities:

- AppFunctions when available;
- explicit intents and deep links;
- Calendar, Files, Media, Notifications, and user-selected Contacts;
- MCP and A2A adapters;
- on-device AI;
- direct and relay transport clients.

### Zone C — EZ Power

Separate application and signature. Contains optional high-privilege capabilities:

- Accessibility UI-tree observation and deterministic user-authored macros;
- MediaProjection;
- Shizuku;
- typed ADB operations.

Power must independently evaluate policy and must block password, OTP, payment, permission, credential-manager, and unknown-package surfaces.

### Zone D — Windows/Linux Nodes

Replaceable workers that may run:

- code, build, test, browser, file, document, and ADB tasks;
- sandboxed CLI agents;
- local model inference;
- GPU training or evaluation.

Nodes never become the authority for personal memory or permanent grants.

### Zone E — Blind Relay

Stores and forwards opaque encrypted envelopes. It may know mailbox identifiers, sizes, TTLs, and timing. It must not see action bodies, personal context, artifacts, or receipts in plaintext.

---

## 4. Repository Target

```text
mobile_agent/
├─ apps/
│  ├─ android-core/
│  ├─ android-power/
│  └─ android-managed/
├─ core/
│  ├─ protocol/
│  ├─ events/
│  ├─ kernel/
│  ├─ authority/
│  ├─ capability/
│  ├─ verification/
│  ├─ crypto/
│  ├─ cas/
│  └─ twin/
├─ personal/
│  ├─ signals/
│  ├─ memory/
│  ├─ adaptation/
│  └─ skills/
├─ models/
│  ├─ edge-litert/
│  ├─ router/
│  ├─ embeddings/
│  └─ manifests/
├─ adapters/
│  ├─ appfunctions/
│  ├─ android-intents/
│  ├─ mcp/
│  ├─ a2a/
│  ├─ providers/
│  └─ legacy-ezmap/
├─ fabric/
│  ├─ protocol/
│  ├─ node-go/
│  ├─ relay-go/
│  └─ tck/
├─ infra/
│  ├─ tailscale/
│  ├─ gpu/
│  ├─ sandbox/
│  └─ release/
├─ tests/
│  ├─ golden-traces/
│  ├─ protocol-tck/
│  ├─ fault-injection/
│  ├─ android-emulator/
│  └─ physical-device/
└─ docs/
```

The migration begins with fewer Gradle modules and splits only when dependency rules are proven. The first mandatory modules are:

```text
:app
:core:protocol
:core:events
:core:kernel
:core:authority
:core:capability
:core:verification
:core:storage
:runtime:android
:adapter:gemini
:adapter:mcp
:legacy:mobility
```

Dependency rules:

- `core:*` must not depend on Android SDK, Gemini SDK, MCP SDK, Room annotations, or network clients.
- Provider adapters convert provider-specific function calls to internal proposals.
- Android permissions are referenced only by `runtime:android` or application modules.
- Capability implementations cannot call model APIs.
- UI observes projections and submits commands; it does not directly mutate durable run state.

---

## 5. EZ-PACT v1 Protocol

### 5.1 Canonical representation

Control messages use deterministic Protocol Buffers for typed schemas. The signed payload is wrapped as a COSE-compatible envelope. JSON is retained only for debugging, export, and interoperability adapters.

Every envelope contains:

```text
schema_version
message_id
thread_id
run_id
parent_message_id
origin_device_id
audience_device_id
issued_at
expires_at
payload_type
payload_hash
trace_context
signature_algorithm
signature
```

### 5.2 Direct transport

Preferred path:

- bidirectional HTTP/2 stream over Tailscale;
- application-level signed envelopes;
- Tailscale ACLs restrict source device, destination node, port, and user;
- node service binds only to loopback and is exposed with Tailscale Serve or a private tailnet address.

### 5.3 Relay transport

Fallback path:

- same signed envelope format;
- end-to-end encrypted body;
- outbound-only node and phone connections;
- bounded mailbox, TTL, duplicate suppression, quota, and acknowledgement cursor;
- relay cannot authorize, modify, or interpret actions.

### 5.4 Artifact transport

Artifacts are separate from control messages.

```text
ArtifactManifest
- artifact_id
- media_type
- total_bytes
- chunk_size
- chunk_hashes
- root_hash
- encryption_metadata
- producer_receipt_id
- retention_class
```

Default chunk size is 4 MiB. SHA-256 is used for content identity. Partial retries fetch only missing or corrupt chunks.

### 5.5 Version compatibility

- Major protocol version mismatch: no effectful execution.
- Minor version mismatch: unknown optional fields are ignored but preserved in journal export.
- Capability major version mismatch: fail closed.
- Capability minor version mismatch: execute only if the node advertises schema compatibility.

---

## 6. Core Domain Types

```kotlin
data class ActionProposal(
    val proposalId: ProposalId,
    val capability: CapabilityRef,
    val input: TypedValue,
    val requestedScope: CapabilityScope,
    val rationaleSummary: String?,
    val source: ProposalSource,
)

data class PreparedAction(
    val proposalId: ProposalId,
    val normalizedInput: TypedValue,
    val exactTargets: List<ResourceRef>,
    val expectedEffects: List<ExpectedEffect>,
    val preconditions: List<Precondition>,
    val requiredScope: CapabilityScope,
    val risk: RiskAssessment,
    val verifier: VerifierRef,
    val idempotencyKey: IdempotencyKey,
    val expiresAt: Instant,
    val digest: Digest,
)

data class ApprovalGrant(
    val grantId: GrantId,
    val preparedDigest: Digest,
    val scope: CapabilityScope,
    val approvedBy: UserAuthority,
    val approvedAt: Instant,
    val expiresAt: Instant,
    val authenticationStrength: AuthenticationStrength,
)

data class ExecutionLease(
    val leaseId: LeaseId,
    val preparedDigest: Digest,
    val nodeId: DeviceId,
    val attempt: Int,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

data class ExecutionReceipt(
    val receiptId: ReceiptId,
    val preparedDigest: Digest,
    val leaseId: LeaseId,
    val nodeId: DeviceId,
    val startedAt: Instant,
    val finishedAt: Instant,
    val outcome: ExecutionOutcome,
    val observations: List<Observation>,
    val artifacts: List<ArtifactRef>,
    val resourceUsage: ResourceUsage,
    val signature: Signature,
)
```

No provider object such as Gemini `FunctionCall` or `JSONObject` may appear in these types.

---

## 7. Durable Event Model

The event journal is append-only. Mutable UI state is a projection.

Required events:

```text
ThreadCreated
TurnAccepted
ContextCapsulePrepared
ModelRequested
ModelDeltaReceived
PlanProposed
ActionProposed
ActionPrepared
PolicyEvaluated
ApprovalRequested
ApprovalGranted
ApprovalDenied
LeaseIssued
CommitIntentRecorded
ExecutionStarted
ExecutionProgressed
ExecutionFinished
ReceiptReceived
VerificationStarted
VerificationPassed
VerificationFailed
CompensationProposed
CompensationFinished
CheckpointWritten
TurnSuspended
TurnCompleted
TurnFailed
TurnCancelled
```

Every effectful transition is persisted before the external action is initiated.

### Restart reconciliation

On process restart:

1. Replay journal to rebuild projections.
2. Find runs ending at `CommitIntentRecorded` or `ExecutionStarted`.
3. Query the target capability or node using the idempotency key.
4. Record `ExecutionFinished`, `VerificationFailed`, or `RunRequiresReview`.
5. Never repeat an effect merely because the previous response was lost.

---

## 8. Concurrency and Resource Locks

Read-only observations may execute concurrently when they declare no shared mutable resources.

Effectful actions are serialized by resource key:

```text
calendar:<account>
contact:<contact-id>
file:<canonical-uri>
notification:<package>
message:<recipient>
app:<package>
node:<node-id>:workspace:<workspace-id>
```

A plan can run parallel branches only when the kernel proves their lock sets are disjoint.

Default behavior for unknown lock sets is sequential execution.

---

## 9. Capability Contract

```kotlin
interface Capability<I : Any, O : Any> {
    val descriptor: CapabilityDescriptor<I, O>

    suspend fun observe(
        input: I,
        context: ExecutionContext,
    ): ObservationResult

    suspend fun prepare(
        input: I,
        context: ExecutionContext,
    ): PreparedAction

    suspend fun commit(
        prepared: PreparedAction,
        grant: ApprovalGrant?,
        lease: ExecutionLease,
    ): ExecutionReceipt<O>

    suspend fun verify(
        prepared: PreparedAction,
        receipt: ExecutionReceipt<O>,
    ): VerificationResult

    suspend fun compensate(
        prepared: PreparedAction,
        receipt: ExecutionReceipt<O>,
    ): CompensationResult
}
```

Capability descriptors include:

```text
capability ID and version
input/output schemas
risk tier
data classes read and written
network destinations
required Android permissions or node privileges
resource-lock derivation
supported simulation level
verifier ID
compensation support
maximum duration and output size
```

Arbitrary shell strings are prohibited. Process capabilities accept a trusted executable reference plus a validated argument vector.

---

## 10. Authority and Policy

Technical permission and agent autonomy are distinct dimensions.

Permission decisions:

```text
DENY
ASK_ONCE
ALLOW_THIS_RUN
ALLOW_UNTIL(time)
ALLOW_SCOPE(scope, expiry)
ALWAYS_FOR_SCOPE(scope)
```

Autonomy modes:

```text
PLAN_ONLY
ASK_BEFORE_EFFECT
SAFE_AUTO_WITHIN_SCOPE
BLOCKED
```

Scope includes:

```text
capability ID and version
package or node
resource/contact/account/file URI
operation: read/create/update/delete/send
personal-data class
origin: UI/voice/share/notification/MCP/remote peer
network destination
time window
run and thread
foreground/background
frequency and cost budget
```

Policy priority:

```text
platform prohibition
→ user hard deny
→ enterprise policy
→ temporary user grant
→ skill scope
→ model suggestion
```

A lower-priority layer cannot override a higher-priority deny.

---

## 11. Personal Context and Memory

Memory is separated into:

- working memory;
- episodic event memory;
- semantic personal facts;
- procedural skills;
- policy memory;
- social entities and relations.

Every durable memory record includes:

```text
provenance
confidence
sensitivity
valid-from and valid-until
last verified
user-confirmed flag
editable/deletable flag
derivation lineage
```

The model never receives the full memory store. `ContextAssembler` selects a minimal context capsule based on current task, scope, sensitivity, freshness, and provider egress policy.

External observations are stored as untrusted evidence. They do not directly mutate user-confirmed facts or policies.

---

## 12. On-Device AI

The first production release does not depend on an unrestricted on-device general agent.

### Tier 0 — deterministic local

- schema validation;
- direct command parsing;
- policy evaluation;
- capability shortlist rules;
- receipt verification.

### Tier 1 — compact local models

- intent classification;
- risk classification;
- PII detection and redaction;
- data-egress classification;
- semantic retrieval;
- routing prediction.

### Tier 2 — local language model

- short summaries;
- plan drafting;
- function selection among already-authorized candidates;
- offline assistance.

### Tier 3 — private nodes

- long-context planning;
- coding and browser tasks;
- VLM tasks;
- large document processing;
- training and evaluation.

### Tier 4 — hosted inference

Used only after explicit data-egress policy evaluation. Provider secrets are not embedded in the APK.

Model packs are independently signed, versioned, benchmarked, activated, and rolled back. The base APK contains no large production model weight.

---

## 13. Node Runtime

Each node exposes a signed `NodeAdvertisement`:

```text
node identity
OS and architecture
runtime version
capability descriptors
available workspaces
CPU/RAM/GPU inventory
model endpoints
sandbox types
health status
queue depth
network policy class
last attestation time
```

### Workspace isolation

Every delegated coding or browser task receives a dedicated workspace:

- Git worktree or disposable checkout;
- sandbox policy;
- explicit input artifact mounts;
- explicit writable output directory;
- network allowlist;
- wall-clock, CPU, RAM, GPU, and output quotas.

No worker receives the user's entire home directory by default.

### GPU serving

All private inference endpoints are normalized behind `ModelEndpoint`:

```text
endpoint ID
protocol type
model IDs
context limits
tool-use support
vision/audio support
health endpoint
queue depth
cost and privacy class
```

Ollama, vLLM, and NVIDIA NIM are adapters behind the same contract.

---

## 14. Android Product Boundaries

### Core

Package proposal: `io.github.cjw0076.ezadaptive`

Distribution: Play AAB and internal signed APK.

Allowed:

- narrow Android APIs;
- AppFunctions with runtime feature detection;
- user-selected files and contacts;
- notification listener with explicit consent;
- MCP/A2A/EZ-PACT;
- local models;
- approval and receipt UI.

Not allowed:

- autonomous general-purpose Accessibility execution;
- unrestricted shell or ADB;
- silent self-update;
- broad contact access without demonstrated core need.

### Power

Package: `io.github.cjw0076.ezadaptive.power`

Distribution: separately signed, owner-installed APK.

Requirements:

- mutual pairing with Core;
- persistent execution indicator;
- package allowlist;
- typed actions only;
- independent policy evaluation;
- no secrets or raw screenshot retention by default;
- immediate kill switch and grant revocation.

### Managed

Package: `io.github.cjw0076.ezadaptive.managed`

For Device Owner/DPC deployments only.

---

## 15. Observability

The phone maintains a user-visible local audit trail. Operational telemetry is a separate, redacted stream.

### Required metrics

```text
run completion rate
verified completion rate
approval rate and denial rate
duplicate-delivery rate
duplicate-effect rate
verification failure rate
reconciliation rate
route fallback rate
node queue latency
model first-token latency
model correction rate
battery cost per active hour
thermal throttling rate
crash-free sessions
ANR rate
```

### Trace context

Every envelope, event, node task, model call, and artifact carries:

```text
trace_id
span_id
thread_id
run_id
proposal_id
prepared_digest
lease_id
```

Telemetry exporters must redact or hash personal identifiers before egress.

---

## 16. Service-Level Objectives

### Core release SLOs

```text
Crash-free user sessions                  >= 99.8%
ANR-free sessions                        >= 99.9%
Local read-only task success             >= 99.5%
Verified effectful task success          >= 99.0%
Duplicate external effects               = 0
Unauthorized scope expansion             = 0
Approval-to-wrong-target incidents       = 0
Journal recovery success                 >= 99.9%
Direct-to-relay route recovery           >= 99.5%
P95 local command response               <= 300 ms
P95 approval preview creation            <= 1.5 s
P95 node task acknowledgement            <= 2 s direct
```

Error-budget exhaustion blocks feature rollout and triggers a stabilization release.

---

## 17. Security Program

Required controls:

- Android authority keys in Android Keystore;
- node identity keys in OS-protected stores where available;
- signed device and capability cards;
- short-lived session keys;
- explicit node and model-pack revocation;
- encrypted Room database;
- no static production API keys in `BuildConfig`;
- dependency and secret scanning;
- SBOM and provenance for every release;
- signed model manifests and node binaries;
- prompt-injection regression corpus;
- capability fuzzing;
- relay replay and tamper tests;
- approval-spoofing tests;
- compromised-node tabletop exercise.

### Data retention classes

```text
EPHEMERAL       deleted after run
SHORT           7 days
PERSONAL        until user deletion
AUDIT           configurable, default 90 days
SECRET          never persisted outside secure store
```

Raw screenshots and accessibility trees default to `EPHEMERAL`.

---

## 18. Release Artifacts

```text
EZ-Adaptive-core-release.aab
EZ-Adaptive-core-internal.apk
EZ-Power-release.apk
EZ-Managed-release.aab

ez-agentd-windows-amd64.exe
ez-agentd-linux-amd64
ez-relay-linux-amd64

ez-edge-base-ko-en.manifest.json
checksums.sha256
sbom.cdx.json
provenance.intoto.jsonl
release-manifest.json
```

Core, Power, Managed, node binary, model pack, and protocol release keys are separate.

---

## 19. Production Rollout

```text
local developer
→ automated emulator
→ physical-device lab
→ internal dogfood
→ 20-user alpha
→ 100-user closed beta
→ 5% staged rollout
→ 25%
→ 100%
```

Promotion requires passing the readiness gates in `PRODUCTION_READINESS_GATES.md`.

Rollback is required at four independent levels:

- APK/AAB version;
- protocol feature flag;
- node binary;
- model pack.

---

## 20. Definition of Production

EZ Adaptive is production-grade only when:

1. A user can inspect, revoke, export, and delete memory and grants.
2. Every effectful action has a prepared digest, authorization evidence, receipt, and verifier result.
3. Process death, duplicate delivery, route changes, and node loss have deterministic recovery semantics.
4. Core remains useful without Power, a desktop, or a cloud model.
5. Power cannot exceed the exact scope granted by Core and independently reevaluates policy.
6. A compromised or hallucinating model cannot directly invoke an actuator.
7. The team can release, monitor, halt, roll back, and reproduce every artifact.
8. Security and reliability metrics are measured against explicit SLOs for multiple releases.

Until then the product is an advanced prototype, regardless of feature count or visual quality.
