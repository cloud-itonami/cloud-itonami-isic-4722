# cloud-itonami-isic-4722

**Retail sale of beverages in specialized stores** — ISIC Rev.4 class 4722.

A coordination-only actor for specialized beverage retail stores — liquor stores, wine shops, and craft-beer shops (distinct from specialized food retail, ISIC 4721, and tobacco retail, ISIC 4723) — behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Features

- **Closed proposal-op allowlist**: `log-sales-record`, `schedule-staffing-operation`, `coordinate-supply-order`, `flag-compliance-concern` (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Store unverified** — the target store's business registration AND liquor retail license must exist AND be independently registered/verified in the store.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — finalizing an age-verification override, finalizing a responsible-service-of-alcohol decision, direct point-of-sale age-verification/ID-scanner actuation, and alcohol-licensing-authority enforcement (liquor-control-board clearance, license issuance/suspension, compliance enforcement) are permanently blocked. A structured-field companion check also inspects the proposal's `:value` for explicit finalization-intent booleans, belt-and-suspenders alongside the free-text scan.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-compliance-concern` — ALWAYS escalates, regardless of confidence or phase. A "flag a concern" op is never auto-commit-eligible and never finalizes an age-verification or responsible-service decision itself — it only surfaces the concern for a human.
  - `:coordinate-supply-order` above a cost threshold — a large-value procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: sales-record logging only (approval-gated)
  - Phase 2: + staffing-operation scheduling, supply-order proposals (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals (compliance concerns and high-cost supply orders always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## Out of scope (structural, not a rollout milestone)

This actor is **operations coordination only**. It never performs or authorizes:

- Finalizing an age-verification override.
- Finalizing a responsible-service-of-alcohol decision (e.g. an over-service cutoff determination).
- Direct point-of-sale age-verification/ID-scanner actuation or control.
- Alcohol-licensing-authority enforcement (liquor-control-board clearance, license issuance/suspension, compliance enforcement).

The governor's `scope-exclusion-violations` check re-scans every proposal for this failure mode independently of the advisor's own framing, and treats it as a HARD, permanent block regardless of confidence or how clean everything else is. A "flag a concern" op (`:flag-compliance-concern`) always escalates to a human and is never in any phase's `:auto` set — it only ever surfaces a concern, it never finalizes an age-verification or responsible-service decision.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/beverageretailops/governor_test.cljk` — unit tests of governor hard checks, scope exclusion, structured-field violations, and a dedicated regression test asserting the default mock-advisor proposals never self-trip scope-exclusion
- `test/beverageretailops/advisor_test.cljk` — advisor proposal shape and consistency
- `test/beverageretailops/phase_test.cljk` — rollout phase logic
- `test/beverageretailops/governor_contract_test.cljk` — full graph integration, audit trail
- `test/beverageretailops/store_contract_test.cljk` — Store protocol and MemStore implementation

## Modules

- `beverageretailops.store` — SSoT (MemStore, String-keyed store directory, append-only ledger)
- `beverageretailops.advisor` — contained intelligence node (mock + real-LLM seam)
- `beverageretailops.governor` — independent compliance layer
- `beverageretailops.phase` — staged rollout (0→3)
- `beverageretailops.operation` — langgraph-clj StateGraph
- `beverageretailops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 2 (coordination/logistics/trade) fleet. See ADR-2607121000 and the `cloud-itonami-isic-4722-beverage-retail-coverage` ADR in `com-junkawasaki/root` for design decisions.
