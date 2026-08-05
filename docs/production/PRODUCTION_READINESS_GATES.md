# EZ Adaptive Production Readiness Gates

This checklist is a release contract, not a roadmap. A release cannot be called production-ready unless every mandatory gate for its channel passes.

---

## Gate A — Architecture

- [ ] Core domain types contain no Gemini, MCP, Android, Room, or JSONObject types.
- [ ] Effectful tools implement typed `prepare`, `commit`, and `verify`.
- [ ] Unknown effectful capability versions fail closed.
- [ ] UI writes commands to the runtime; UI does not directly mutate durable run state.
- [ ] Canonical state can be rebuilt from an append-only event journal.
- [ ] Resource locks are declared for every effectful capability.
- [ ] Power, Core, and Managed are separate app modules/packages.

Evidence:

```text
architecture dependency test
module graph report
capability descriptor inventory
journal replay test report
```

---

## Gate B — Authority and Safety

- [ ] Default capability decision is DENY or ASK, never implicit ALWAYS.
- [ ] Every effectful operation is bound to a prepared digest.
- [ ] Approval mutation test rejects changed recipient, path, amount, package, or operation.
- [ ] Approval expiry is enforced.
- [ ] Biometric or device credential is required for high-risk actions.
- [ ] External content cannot create an effectful goal or grant.
- [ ] Learning cannot widen scope.
- [ ] Power independently evaluates policy.
- [ ] Password, OTP, payment, permission, and credential surfaces are blocked in Power.

Release threshold:

```text
unauthorized scope escapes      0
wrong-target approvals          0
duplicate external effects      0
```

---

## Gate C — Durability

Mandatory fault tests:

- [ ] Kill Android process after `ActionPrepared`.
- [ ] Kill after `ApprovalGranted`.
- [ ] Kill after `CommitIntentRecorded` but before node response.
- [ ] Kill node during execution.
- [ ] Deliver the same message 10 times.
- [ ] Deliver messages out of order.
- [ ] Change route from direct to relay during a run.
- [ ] Disconnect for 24 hours and recover durable outbox.
- [ ] Expire an execution lease during node execution.
- [ ] Revoke node before commit.

Expected properties:

```text
no blind re-execution
no duplicate effect
no lost terminal state
reconciliation result is user-visible
```

---

## Gate D — Verification

- [ ] Every effectful capability has a verifier.
- [ ] Verifier failure produces `DIVERGED` or `FAILED`, never success.
- [ ] Model text cannot set terminal success.
- [ ] Artifacts include hash, size, media type, producer, and receipt reference.
- [ ] File writes are verified against canonical URI and content hash.
- [ ] Calendar/contact/message operations are re-read from the authoritative provider where possible.
- [ ] Node process tasks record trusted executable, argv, exit code, stdout/stderr hash, and resource usage.

Metric:

```text
verified-receipt coverage for effectful actions = 100%
```

---

## Gate E — Protocol

Protocol TCK must test Android/Kotlin and Go implementations against the same vectors.

- [ ] Deterministic serialization produces identical bytes.
- [ ] Signature verification passes across implementations.
- [ ] Tampered payload, audience, TTL, and parent hash fail.
- [ ] Major version mismatch blocks effectful execution.
- [ ] Unknown optional fields survive journal export.
- [ ] Direct and relay transport produce identical prepared digests.
- [ ] Artifact partial retry reconstructs the same root hash.
- [ ] Replay cache survives process restart.

---

## Gate F — Node Isolation

- [ ] Coding tasks use isolated Git worktrees.
- [ ] Browser tasks use disposable profiles or containers.
- [ ] Input mounts are read-only unless explicitly writable.
- [ ] Output path is explicit and quota-limited.
- [ ] Network is denied by default and allowlisted per task.
- [ ] Trusted executables are resolved before execution.
- [ ] Arbitrary shell strings are unavailable.
- [ ] Path traversal and symlink escape tests pass.
- [ ] CPU, RAM, wall-clock, GPU, and output quotas are enforced.
- [ ] Workspace deletion and retention policies are tested.

---

## Gate G — On-Device AI

- [ ] Core safety and policy paths work with all models disabled.
- [ ] Intent, PII, risk, and route models have versioned evaluation datasets.
- [ ] Model packs are signed and hash-verified.
- [ ] Model activation includes device-class latency and thermal smoke tests.
- [ ] Model rollback does not require APK rollback.
- [ ] Local model output is schema-validated.
- [ ] Hosted route is labeled as data egress and requires policy evaluation.
- [ ] Provider secrets are absent from APK resources and BuildConfig.

Initial quality bars:

```text
PII recall on blocking corpus       >= 0.98
risk recall for high-risk actions  >= 0.99
route policy violation             = 0
```

---

## Gate H — Android Quality

- [ ] Target and compile SDK match current release policy.
- [ ] Foreground-service types and user-visible notifications are correct.
- [ ] WorkManager is used only for deferrable work.
- [ ] Background-start restrictions are tested.
- [ ] Predictive Back and edge-to-edge behavior pass.
- [ ] Dynamic type at 200% remains usable.
- [ ] TalkBack labels and traversal order pass.
- [ ] Reduced-motion mode is implemented.
- [ ] Foldable/tablet list-detail layout passes.
- [ ] Battery and thermal tests run on low, mid, and high device classes.

Quality thresholds:

```text
crash-free sessions       >= 99.8%
ANR-free sessions         >= 99.9%
P95 local command         <= 300 ms
```

---

## Gate I — Privacy and User Control

- [ ] User can see every paired node.
- [ ] User can revoke a node immediately.
- [ ] User can inspect, edit, and delete personal memory.
- [ ] User can inspect and revoke grants by scope.
- [ ] User can export the local audit history.
- [ ] Raw screenshots and accessibility trees are ephemeral by default.
- [ ] Telemetry is opt-in where required and redacted before export.
- [ ] Account deletion and local-only use are supported.
- [ ] Retention classes are enforced by automated cleanup.

---

## Gate J — Supply Chain

Every release produces:

```text
signed AAB/APK or binary
checksums.sha256
SBOM (CycloneDX)
SLSA/in-toto provenance
release manifest
third-party license inventory
model manifest inventory
migration and rollback notes
```

- [ ] Release build is reproducible within documented tolerances.
- [ ] Signing occurs only in a protected environment.
- [ ] Core, Power, Managed, nodes, and model packs use distinct keys.
- [ ] Dependency vulnerability scan has no unaccepted critical findings.
- [ ] Secret scan passes.
- [ ] Release artifact can be installed from a clean machine/device.
- [ ] Previous version rollback is rehearsed.

---

## Gate K — Operations

- [ ] Dashboards exist for verified completion, duplicate delivery, duplicate effect, route fallback, node health, queue latency, model correction, crash, ANR, and battery.
- [ ] Alerts are linked to runbooks.
- [ ] Node and model-pack kill switches are tested.
- [ ] Feature flags are signed or retrieved from an authenticated source.
- [ ] A staged rollout can be halted without a new APK.
- [ ] An incident owner and escalation channel are named.
- [ ] Security incident, relay outage, compromised node, and corrupt model-pack drills have been run.

---

## Channel Requirements

### Internal APK

Mandatory gates: A, B, C, D, E, F, H basic, J basic.

### Closed Beta

Mandatory gates: all internal gates plus G, H full, I, K dashboards and kill switches.

### Production

All gates mandatory. In addition:

- [ ] 30 consecutive dogfood days without a critical safety incident.
- [ ] 10,000 verified effectful operations with zero duplicate effect.
- [ ] Two successful node-binary rollbacks.
- [ ] Two successful model-pack rollbacks.
- [ ] One complete compromised-node incident drill.
- [ ] Error budget remained within SLO for two release cycles.

---

## Go / No-Go Sign-off

Required signatories:

```text
runtime owner
Android owner
security owner
release owner
product owner
```

A feature-complete build without this evidence is a beta, not production.
