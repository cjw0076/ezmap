# EZ Adaptive 24-Week Production Implementation Plan

This is an execution plan with mergeable PR boundaries and exit evidence. It intentionally prioritizes safety, durability, and operability before broad device control.

---

## Team Shape

Minimum sustained team:

```text
1 Android/runtime lead
1 Android/product engineer
1 distributed-systems/node engineer
1 ML/on-device engineer
0.5 security/release engineer
0.5 design/QA engineer
```

With fewer than four full-time engineers, keep the same dependency order and extend the schedule rather than merging safety boundaries.

---

## Workstream Ownership

| Workstream | Primary | Secondary |
|---|---|---|
| Protocol/events/kernel | Runtime lead | Node engineer |
| Android Core/UI | Android product | Runtime lead |
| Windows/Linux nodes | Node engineer | Runtime lead |
| On-device AI | ML engineer | Android product |
| Security/release | Security owner | All owners |
| Test/fault injection | QA/design | Runtime lead |

---

# Phase 0 — Stabilize Current Product (Weeks 1–2)

## PR 0.1 — Safety Freeze

Changes:

- replace all default `ALWAYS` permissions with deny/ask migration;
- block `send_message`, contact mutation, schedule delete, profile mutation, call, and alarm from direct execution;
- introduce a temporary `LegacyEffectApproval` callback;
- disable effectful parallel execution;
- fail closed for unknown effectful MCP tools;
- remove production provider keys from APK build fields;
- add a local database migration for permission settings.

Exit evidence:

```text
legacy effectful tools cannot run without explicit approval
permission migration test passes from existing install
unknown MCP effect test passes
secret scan passes
```

## PR 0.2 — Golden Trace Capture

Capture 50 representative existing scenarios:

```text
read-only chat
voice command
navigation query
calendar read
note create/delete
message draft
contact lookup/update
MCP read/effect
skill execution
process death during a run
```

Artifacts:

```text
input.json
normalized proposal.json
legacy tool calls.json
user-visible result.json
expected side effects.json
```

These traces become migration regression tests.

---

# Phase 1 — Extract the Kernel (Weeks 3–6)

## PR 1.1 — Multi-module Skeleton

Create:

```text
:core:protocol
:core:events
:core:kernel
:core:authority
:core:capability
:core:verification
:core:storage
:adapter:gemini
:adapter:mcp
:legacy:mobility
```

Add dependency tests that fail CI if core imports forbidden packages.

## PR 1.2 — Provider-neutral Proposal Types

- introduce `ActionProposal`, `PreparedAction`, `ApprovalGrant`, `ExecutionLease`, and `ExecutionReceipt`;
- add Gemini conversion adapter;
- retain old `JSONObject` calls only inside the legacy adapter;
- add canonical serializer test vectors.

Exit:

```text
all golden traces can be represented without Gemini types in core
Kotlin/JVM compilation succeeds without Android SDK for core modules
```

## PR 1.3 — Append-only Event Journal

- define all durable event types;
- implement reducer and checkpoints;
- project legacy `AgentRun` from new events;
- add Room-backed journal adapter;
- add replay and corruption-detection tests.

Exit:

```text
same event history produces byte-equivalent projection
process restart restores all nonterminal states
```

## PR 1.4 — ThreadActor Runtime

- one actor per thread;
- command queue;
- cancellation and suspension;
- resource locks;
- no direct UI state mutation;
- structured progress events.

Exit:

```text
100 concurrent read-only test threads
serialized conflict tests
no race under kotlinx-coroutines-test stress
```

---

# Phase 2 — Typed Capabilities and Verification (Weeks 7–10)

## PR 2.1 — Capability Registry

Implement descriptor, schema validation, resource locks, risk, privilege requirements, and verifier references.

Migrate first:

```text
get_schedule          read-only
get_notes             read-only
save_note             reversible local effect
create_schedule       provider-backed effect
```

## PR 2.2 — Prepare / Commit / Verify

For the four pilot capabilities:

- `prepare` computes exact target and preview;
- authority kernel evaluates scope;
- `commit` uses idempotency key;
- verifier re-reads authoritative state;
- receipt is persisted and displayed.

## PR 2.3 — Approval UI v1

Display:

```text
exact action verb
target identity
before/after or expected effect
data leaving device
execution node
permission duration
reversibility
```

High-risk actions require biometric/device credential.

Exit:

```text
recipient/path mutation tests fail approval
expired approval fails
verification failure cannot render success
```

## PR 2.4 — Legacy Compatibility Adapter

All remaining old tools run through a compatibility capability that is:

- sequential;
- approval-required for effects;
- marked `legacy_unverified` when no verifier exists;
- excluded from safe automation.

This keeps product functionality while migration continues.

---

# Phase 3 — EZ-PACT and Private Nodes (Weeks 11–14)

## PR 3.1 — Device Identity and Pairing

- Ed25519 device identities;
- QR/device-code pairing;
- signed device card;
- node revocation;
- Android Keystore storage;
- migration from shared secret to dual-stack identity.

## PR 3.2 — Protocol v1

- deterministic protobuf messages;
- signed envelope;
- replay cache;
- expiry and audience checks;
- direct transport;
- protocol TCK shared by Kotlin and Go.

## PR 3.3 — Node Advertisement and Routing

Node advertises:

```text
capabilities
CPU/RAM/GPU
models
workspaces
queue depth
health
sandbox support
```

Router evaluates only policy-eligible candidates.

## PR 3.4 — Execution Lease and Reconciliation

- issue one commit lease;
- node durable task journal;
- query by idempotency key;
- recover from node or phone restart;
- prevent dual-node commit.

## PR 3.5 — Blind Relay

- encrypted envelopes;
- outbound-only clients;
- bounded mailbox;
- cursor acknowledgements;
- duplicate suppression;
- quotas and TTL;
- direct-to-relay route switching test.

Exit:

```text
10x duplicate delivery creates one effect
route change preserves prepared digest
relay tamper test fails authentication
24-hour offline outbox recovers
```

---

# Phase 4 — Sandboxed Desktop/GPU Execution (Weeks 15–17)

## PR 4.1 — Workspace Service

- Git worktree lifecycle;
- explicit input/output mounts;
- quotas;
- cleanup and retention;
- trusted executable catalog;
- no arbitrary shell string API.

## PR 4.2 — Process Capability

Typed input:

```text
workspace ID
trusted executable ID
argv
working directory
environment allowlist
network policy
resource limits
expected artifacts
```

Verifier records exit code and artifact hashes.

## PR 4.3 — Model Endpoint Capability

Adapters:

```text
Ollama
vLLM
NVIDIA NIM
hosted OpenAI-compatible endpoint
```

Every endpoint is labeled by privacy class and data-egress policy.

## PR 4.4 — GPU Scheduling

- advertise free VRAM and queue depth;
- execution reservation;
- timeout and cancellation;
- OOM classification;
- model health and warmup;
- artifact return.

Exit:

```text
workspace escape tests pass
network deny test passes
GPU OOM produces classified failure and no false success
```

---

# Phase 5 — On-device AI and Personal Context (Weeks 18–20)

## PR 5.1 — Context Capsule

- typed personal facts;
- provenance, confidence, sensitivity, validity interval;
- retrieval and minimization;
- provider egress filter;
- user inspection and deletion.

## PR 5.2 — Edge Classifiers

Models:

```text
intent
risk
PII
data-egress
route ranking
```

Each model has a versioned dataset, threshold, abstention policy, and regression report.

## PR 5.3 — Signed Model Packs

- manifest schema;
- signing and verification;
- staged download;
- device benchmark;
- activation and rollback;
- local cache quotas.

## PR 5.4 — Local LLM Adapter

Use only for:

- concise summaries;
- plan drafting;
- shortlist selection;
- offline assistance.

It cannot bypass policy or schema validation.

Exit:

```text
Core safety works with all ML disabled
PII/risk release thresholds pass
model rollback without APK rollback passes
```

---

# Phase 6 — Power and Shadow Twin (Weeks 21–22)

## PR 6.1 — Separate EZ Power APK

- independent package and signing;
- mutual pairing with Core;
- persistent execution notification;
- package allowlist;
- kill switch;
- typed Accessibility/MediaProjection/Shizuku actions.

## PR 6.2 — Sensitive Surface Blocker

Block:

```text
password
OTP
payment
permission dialog
credential manager
banking package
unknown package
```

## PR 6.3 — Shadow Execution

- emulator or mirrored UI-tree dry run;
- predicted vs observed delta;
- explicit divergence result;
- no automatic promotion after one success.

Exit:

```text
sensitive-surface escape = 0
Power cannot execute an unpaired or expired grant
shadow divergence blocks real commit
```

---

# Phase 7 — Release Engineering and Beta (Weeks 23–24)

## PR 7.1 — Release Pipeline

Produce:

```text
Core AAB/internal APK
Power APK
node binaries
relay binary
model manifests
checksums
CycloneDX SBOM
in-toto provenance
release manifest
```

## PR 7.2 — Self-hosted Runners

- Windows Android/ADB runner;
- Linux GPU runner over Tailscale;
- protected signing runner;
- clean-environment reproducibility job.

## PR 7.3 — Operational Dashboards and Runbooks

Dashboards:

```text
verified completion
duplicate delivery/effect
verification failure
route fallback
node health
model correction
crash/ANR
battery/thermal
```

Runbooks:

```text
relay outage
compromised node
bad model pack
bad APK rollout
stuck execution lease
journal migration failure
```

## PR 7.4 — Closed Beta Gate

Requirements:

```text
20 internal users
30-day dogfood
10,000 verified effectful actions
0 duplicate effects
0 authority escapes
successful APK, node, and model rollbacks
```

---

# Migration Order for Existing Tools

## Wave A — Read-only

```text
get_schedule
get_notes
get_user_preferences
get_user_profile
lookup_contact
weather/air quality/exchange rate
knowledge and URL fetch
traffic observations
current_track
```

## Wave B — Local reversible

```text
save_note
update preference
manage favorites
```

## Wave C — Provider-backed effects

```text
create/update/delete schedule
contact mutation
alarm
message draft
```

## Wave D — External communication and device action

```text
send message
make call
music control
Power UI actions
remote process and browser tasks
```

Wave D cannot be enabled for safe automation before production gates B–F pass.

---

# Weekly Engineering Cadence

```text
Monday     risk and interface review
Tuesday    implementation
Wednesday  fault and adversarial tests
Thursday   physical-device and node integration
Friday     evidence review, merge, release note
```

Every PR includes:

```text
architecture impact
threat-model delta
migration and rollback
unit/integration/fault tests
metrics added
user-visible audit behavior
```

---

# Decision Rules

- Do not add a new actuator until its verifier and resource locks exist.
- Do not add a new model until a deterministic fallback exists.
- Do not add a new transport without protocol TCK equivalence.
- Do not add a Power feature to Core for convenience.
- Do not call a feature production based on a happy-path demo.
- When schedule pressure conflicts with authority or duplicate-effect safety, reduce scope rather than removing the gate.
