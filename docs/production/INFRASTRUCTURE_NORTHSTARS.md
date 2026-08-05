# EZ Adaptive — Infrastructure North Stars

Status: Product/architecture north star
Scope: Android, iOS, Windows, Linux/GPU, relay, on-device AI, multi-agent runtime, personal life integrations

## Master North Star

> One personal sovereign domain, one connected life graph, many specialized agents, many execution nodes, and one evidence ledger.

User-facing promise:

> Explain once. Delegate anywhere. EZ completes the goal across apps and devices, stays inside the user's authority, and returns verifiable evidence.

Primary metric:

```text
Verified Life Delegation Rate (VLDR)
= weighted eligible personal-life goal episodes
  completed end-to-end
  with postcondition verification
  with no scope violation
  and no user correction within 24 hours
  / all delegated eligible goal episodes
```

Value companion metric:

```text
Verified Hours Returned (VHR)
= estimated user time saved by episodes counted in VLDR
```

Absolute guardrails:

```text
Unauthorized external effects          = 0
Duplicate external effects             = 0
Approval bound to wrong target         = 0
False "verified" success               = 0
Silent personal-data egress            = 0
Learned privilege expansion            = 0
```

## Architectural Formula

```text
Personal Sovereign Domain
= Root Identity
+ Life Graph
+ Authority Kernel
+ Multi-Agent Global-Section Runtime
+ Capability Mesh
+ Compute Fabric
+ Evidence Ledger
```

The product is fully personalized, but not uniformly privileged. Android and iOS share semantics, memory, policy, agent coordination, and network protocols; each platform exposes a different actuation ceiling.

---

## Plane A — Sovereignty

### 1. Personal Sovereign Identity

Mission:

> The user, not an app store, model vendor, desktop, or cloud account, owns the durable identity of the agent.

North star:

> A user can replace every model and execution node without losing personal identity, memory authority, grants, or history.

Target design:

- one user-owned root identity;
- hardware-backed device subkeys on Android and iOS;
- signed device cards and capability cards;
- explicit pairing and revocation;
- recovery kit and social/device-assisted recovery;
- no long-lived bearer token as the primary trust root.

KPIs:

```text
Device pairing completion                 >= 95%
Device revocation propagation             P95 < 10 s online
Key recovery success                      >= 99%
Unrecoverable identity-loss incidents     < 0.1%
Unknown/unattested node execution          = 0
```

### 2. Personal Life Graph

Mission:

> Convert fragmented personal context into a user-editable model of people, places, projects, routines, devices, services, goals, and policies.

North star:

> The user stops re-explaining stable personal context, while stale or uncertain knowledge never silently becomes truth.

Core record contract:

```text
subject
predicate
value
confidence
provenance
sensitivity
validity interval
user-confirmed flag
correction lineage
```

KPIs:

```text
Entity-resolution precision              >= 98%
User correction rate on recalled facts   < 2%
Stale-fact action incidence               = 0
Context capsule token reduction           >= 90% vs raw history
User-visible provenance coverage          100% for durable facts
```

### 3. Authority Kernel

Mission:

> Separate adaptive cognition from non-adaptive authority.

North star:

> Personalization reduces friction without increasing privilege.

The kernel owns:

- typed scope;
- policy precedence;
- approval digest;
- resource locks;
- execution leases;
- idempotency;
- receipt verification;
- revocation;
- compensation eligibility.

KPIs:

```text
Effectful actions with typed scope        100%
Effectful actions with prepared digest    100%
Effectful actions with verifier           100%
Privilege expansion by learning           0
Policy-decision determinism               100% for identical inputs
Approval preview P95                      < 1.5 s
```

---

## Plane B — Cognition

### 4. Multi-Agent Runtime

Mission:

> Use many specialized agents without producing locally plausible but globally inconsistent actions.

Agents are roles, not independent sovereigns:

```text
Coordinator
Context Curator
Planner
Researcher
Executor
Verifier
Memory Curator
Policy Guardian
Recovery Agent
```

North star:

> All local agent outputs glue into one globally consistent commit contract before any external effect.

Global Section Gate:

Before commit, the runtime checks agreement on:

```text
goal
target identity
capability version
scope
data boundary
resource locks
expected effects
verifier
rollback/compensation plan
```

An inconsistency becomes an explicit obstruction object, never a silent merge.

Primary infrastructure metric:

```text
Global Section Completion Rate (GSCR)
= multi-agent runs reaching a consistent verified commit contract
  without human repair
  / eligible multi-agent runs
```

KPIs:

```text
GSCR                                    >= 95%
Undetected conflicting target/scope       = 0
Cross-agent handoff success              >= 99%
Duplicate work caused by coordination    < 1%
User-visible obstruction explanations    100%
```

### 5. Adaptive Personalization Engine

Mission:

> Make intervention decay over time, while permission remains constant unless the user changes it.

Adapts:

- entity interpretation;
- timing and interruption cost;
- preferred modality;
- verified routine selection;
- compute routing;
- summary depth;
- recommendation ranking.

Does not adapt:

- hard-deny policies;
- financial/payment authority;
- external-send authority;
- access to new people, files, accounts, or apps;
- Power/Managed privilege.

North star:

> The same class of task needs less user effort each week, but the granted scope never broadens by itself.

KPIs:

```text
Median interventions per recurring task   decreases >= 50% in 30 days
Suggestion acceptance rate                >= 60%
Unwanted proactive interruption rate      < 5%
Routine replay verification success       >= 99%
Learned scope expansion                    = 0
```

### 6. On-Device AI — EZ Edge

Mission:

> Keep private reflexes local and move only the computation that truly needs a larger model.

Always-local target set:

- intent classification;
- risk classification;
- PII and data-egress detection;
- semantic retrieval;
- capability shortlist;
- policy inputs;
- receipt verification;
- short offline commands.

North star:

> Most everyday decisions complete locally in under one second without exposing personal context.

KPIs:

```text
Local classification coverage             >= 95%
P95 local intent/risk result               < 150 ms
P95 local short response                   < 800 ms
Sensitive request local/redacted handling  >= 99%
Battery cost per active hour               < 3%
Thermal-throttled sessions                 < 2%
```

### 7. Model Infrastructure

Mission:

> Treat models as replaceable, benchmarked capability providers rather than the product identity.

North star:

> A model upgrade can improve quality without changing authority semantics or requiring an APK update.

Requirements:

- signed model manifests;
- device-class compatibility;
- quantization variants;
- rollback independent of app release;
- offline benchmark suite;
- correction-rate monitoring;
- provider-neutral internal frames.

KPIs:

```text
Model-pack rollback success               100%
APK-independent model rollout             100%
Unsupported-device activation             = 0
Regression caught before 25% rollout      >= 99%
Provider switch without kernel changes    100%
```

---

## Plane C — Actuation

### 8. Capability Mesh

Mission:

> Replace raw screen control and shell access with typed, versioned, verifiable capabilities.

Priority order:

```text
semantic platform API
→ app-owned API / App Intents / AppFunctions
→ content provider / system role
→ explicit intent / deep link
→ MCP / A2A integration
→ deterministic user-authored skill
→ Power UI/ADB capability
→ coordinate fallback
```

North star:

> The system controls outcomes, not pixels.

KPIs:

```text
Effectful capability verifier coverage    100%
Semantic API share of successful actions  >= 85%
GUI fallback share                        < 10%
Coordinate fallback share                 < 1%
Unknown capability execution              = 0
Median new connector integration          < 5 engineering days
```

### 9. Android Core

Mission:

> Be the highest-capability user-owned personal authority and executor that remains useful without Power, desktop, or cloud.

North star:

> One Android app completes the majority of daily personal digital tasks with native APIs, local AI, and verified results.

Core targets:

- Compose-native task/run/approval UI;
- AppFunctions feature detection;
- files, calendar, media, notifications, share sheet, deep links;
- local-first event ledger;
- direct and relay networking;
- widgets, quick settings, voice and share entry points;
- foreground service only for user-visible active work.

KPIs:

```text
Core-only eligible task coverage          >= 70%
Crash-free sessions                       >= 99.8%
ANR-free sessions                         >= 99.9%
OEM compatibility on target devices       >= 95%
Network-off read-only task completion     >= 95%
```

### 10. Android Power

Mission:

> Provide an explicit user-owned high-capability lane without turning privilege into unrestricted autonomy.

North star:

> Power reaches UI-only actions while remaining typed, observable, revocable, and safer than a general accessibility macro.

Requirements:

- separate APK and signing key;
- mutual pairing with Core;
- package allowlist;
- sensitive-surface classifier;
- persistent action indicator;
- per-action typed input;
- immediate kill switch;
- raw screenshot and UI-tree default retention: ephemeral;
- independent policy evaluation.

KPIs:

```text
Sensitive-surface block recall            100% in test corpus
Power action receipt coverage             100%
Unknown-package actions                   = 0
Persistent-indicator coverage             100%
Kill-switch propagation                   P95 < 1 s
```

### 11. iOS Native

Mission:

> Preserve the same personal identity, life graph, agents, and evidence model on iOS while using iOS-native action surfaces.

North star:

> Intent continuity across iPhone, Mac, and private nodes—not imitation of unrestricted Android control.

Primary surfaces:

- SwiftUI authority/approval app;
- App Intents and App Entities;
- Shortcuts composition;
- share extension;
- notification actions;
- widgets and controls;
- Focus integration;
- background/long-running intents where the platform permits;
- delegation to Mac/Windows/Linux nodes for unsupported actions.

KPIs:

```text
iOS eligible task coverage                >= 50%
App Intent success                        >= 99%
Shortcut handoff success                  >= 98%
Unsupported action delegated successfully >= 95%
Android/iOS semantic parity               100% for shared capability contracts
```

### 12. Connected-Life Domain Packs

Mission:

> Connect personal life by domain, with an explicit autonomy ceiling for each domain.

Initial packs:

```text
Knowledge      links, PDFs, notes, research
Schedule       calendar, reminders, routines
Communication  contacts, drafts, approved sends
Work           repositories, documents, meetings
Mobility       routes, places, transit
Media          playback and queues
Home           user-owned IoT and automations
Health         read/summarize first; narrow writes
Finance        read-only first; no autonomous transaction
```

North star:

> A user's life goal spans domains without the user manually moving context between apps.

KPIs:

```text
Cross-domain goal completion              >= 90%
Manual copy/paste steps per goal           decreases >= 80%
Domain connector health                   >= 99.5%
High-risk domain autonomous transactions  = 0 until separately certified
```

---

## Plane D — Fabric

### 13. EZ-PACT Communication

Mission:

> Carry intent, authority, execution lease, progress, artifact identity, and proof across unreliable paths.

North star:

> The transport path becomes invisible and cannot change the meaning or multiplicity of an action.

Paths:

```text
Tailscale direct
→ private HTTPS/HTTP2
→ SSH bridge
→ blind reverse relay
→ durable offline outbox
```

KPIs:

```text
Direct acknowledgement P95                < 2 s
Direct-to-relay continuation              >= 99.5%
Duplicate delivery tolerated              100%
Duplicate external effects                = 0
Online state convergence P95              < 5 s
Offline outbox successful recovery        >= 99.9%
```

### 14. Personal State Replication

Mission:

> Let Android, iOS, and user-owned nodes share one personal state without pretending every datum is mergeable.

North star:

> Observations converge automatically; authority conflicts become explicit and require deterministic resolution.

Replication classes:

```text
CRDT-safe        observations, tags, read markers
single-writer    grants, revocations, effectful skill activation
conflict-object  competing semantic facts and goals
ephemeral        active UI, screenshots, temporary model state
```

KPIs:

```text
Lost confirmed edits                     = 0
Silent policy conflict                   = 0
Online convergence P95                   < 5 s
Offline convergence after reconnect P95  < 30 s
Conflict provenance coverage             100%
```

### 15. Artifact and Data Plane

Mission:

> Move large files and outputs reliably without coupling them to control messages.

North star:

> Every artifact is content-addressed, integrity-verifiable, resumable, and governed by retention policy.

KPIs:

```text
Artifact integrity verification           100%
Partial-transfer recovery                 >= 99.9%
Full retransfers caused by one bad chunk  < 0.1%
Unreferenced artifact cleanup             >= 99.9%
Retention-policy compliance               100%
```

### 16. Private Compute Fabric

Mission:

> Route each task to the best user-owned device by privacy, capability, latency, battery, thermal state, queue, and cost.

North star:

> Computation follows the task while personal authority remains fixed.

Node classes:

```text
Android/iPhone edge
Mac personal node
Windows workstation
Linux GPU node
optional hosted provider
```

KPIs:

```text
Private/on-device execution share         >= 80%
Route decision correction rate            < 3%
Node task acknowledgement P95             < 2 s direct
GPU queue prediction error                < 20%
Automatic route failover success          >= 99.5%
```

---

## Plane E — Proof and Operations

### 17. Causal Twin and Verification

Mission:

> Compare predicted and observed change before trusting an action.

North star:

> High-risk behavior is proven in a shadow world or narrow dry-run before real-world commit.

Simulation ladder:

```text
L0 schema/policy validation
L1 state-diff simulation
L2 provider dry-run or draft
L3 emulator/UI-tree shadow execution
L4 disposable worktree/browser/container
```

KPIs:

```text
Effectful verifier coverage               100%
High-risk simulation coverage             >= 95%
Prediction/observation divergence caught  >= 99%
False verified success                    = 0
Successful compensation when supported    >= 99%
```

### 18. Evidence Ledger and Observability

Mission:

> Make every system claim reproducible from events, receipts, artifacts, and verifier results.

North star:

> The product never asks the user to trust an invisible agent state.

KPIs:

```text
Trace completeness                        100% for effectful runs
Receipt signature verification            100%
Run reproduction from exported evidence   >= 99%
Telemetry personal-data leakage           = 0
Mean time to explain a failed run          < 10 min
```

### 19. Developer Platform and Skill SDK

Mission:

> Allow new capabilities and skills without allowing plugins to enter the authority kernel.

North star:

> A third party can add a useful connector quickly, but cannot bypass scope, policy, simulation, or verification.

SDK deliverables:

- capability descriptor schema;
- TypeScript, Kotlin, Swift, Go SDKs;
- local conformance runner;
- signed capability cards;
- verifier templates;
- sandbox and network policy templates;
- malicious-plugin test suite.

KPIs:

```text
Median connector integration time         < 5 days
Protocol conformance pass before publish  100%
Unsigned production plugin execution      = 0
Plugin-caused authority escape            = 0
SDK backward compatibility                >= 2 minor versions
```

### 20. Release and Distribution

Mission:

> Give frontier users a user-owned capability channel without treating distribution freedom as permission to remove safety boundaries.

North star:

> Every binary, model pack, node runtime, and protocol feature is signed, reproducible, staged, observable, and independently rollbackable.

Android lanes:

```text
EZ Adaptive Core  — Play AAB + signed direct APK
EZ Power          — separately signed direct APK
EZ Managed        — enterprise/DPC distribution
```

iOS lanes:

```text
App Store / unlisted app
TestFlight for beta
private Apple Business/School distribution where applicable
alternative marketplace or web distribution only where eligible
enterprise program only for qualifying internal-use organizations
```

Distribution is not a mechanism for bypassing OS consent, code-signing, notarization, developer verification, or the product's own authority kernel.

KPIs:

```text
Reproducible release artifact match       100%
SBOM/provenance coverage                  100%
Staged rollback completion                < 30 min
Unsigned update acceptance                = 0
APK/IPA certificate mismatch install      = 0
Model/node rollback success               100%
```

---

## Platform Strategy

### Android

Android is the first full-capability platform because direct APK distribution can support a separately signed Power lane. The production strategy is not one unrestricted APK. It is a paired system:

```text
Core = authority, memory, native APIs, network, local AI
Power = explicit high-privilege executor
```

The user can install the direct APK channel, but the developer identity and package names must still be registered and verifiable for future certified-device requirements.

### iOS

iOS cannot consume an APK. The same personal domain is delivered as a native SwiftUI app with App Intents, Shortcuts, extensions, and remote-node delegation. Alternative distribution is region-dependent and still requires Apple-controlled signing/notarization/licensing. Enterprise distribution is not a public bypass channel.

### Shared Cross-Platform Core

Recommended implementation split:

```text
Kotlin Multiplatform pure domain core
- protocol types
- event reducer
- policy DSL
- memory model
- context selection
- routing contracts

Android
- Kotlin + Jetpack Compose
- Android Keystore
- Room/SQLCipher
- Android capabilities

iOS
- Swift + SwiftUI
- Secure Enclave/Keychain
- App Intents/Shortcuts/extensions
- native iOS capabilities

Nodes
- Go or Rust worker runtime
```

The shared goal is semantic parity, not UI or privilege parity.

---

## 12-Month Product Targets

```text
VLDR                                      >= 70% for supported recurring tasks
Verified Hours Returned                   >= 5 hours/user/week for power users
Global Section Completion Rate            >= 95%
Core-only Android task coverage            >= 70%
iOS eligible task coverage                 >= 50%
Private/on-device execution share          >= 80%
Effectful verifier coverage                100%
Unauthorized or duplicate external effects 0
30-day recurring-task intervention decay   >= 50%
```

## Decision Rule

Every infrastructure proposal must answer:

1. Does it increase verified life delegation?
2. Does it reduce explanation or intervention?
3. Does it preserve or narrow authority?
4. Does it improve evidence or recovery?
5. Does it work across platform and model replacement?

If a feature increases raw action count but weakens authority, verification, convergence, or user control, it is not aligned with the north star.
