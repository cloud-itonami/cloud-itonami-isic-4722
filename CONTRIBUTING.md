# Contributing to cloud-itonami-isic-4722

Contributions should preserve the actor's scope: specialized beverage
retail back-office coordination only, with CRITICAL exclusions of
age-verification-override finalization, responsible-service-of-alcohol
decision finalization, and direct point-of-sale age-verification/
ID-scanner actuation (see README.md).

- All code must be `.cljc` (portable Clojure, no JVM-only constructs).
- Tests must pass: `kbb -M:test`
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize an age-verification override or otherwise stand in for a
  responsible-service-of-alcohol authority.
- Finalize a responsible-service-of-alcohol decision (e.g. an
  over-service cutoff determination).
- Directly actuate or control point-of-sale age-verification/ID-scanner
  hardware.
- Perform alcohol-licensing-authority enforcement (liquor-control-board
  clearance, license issuance/suspension, compliance enforcement).

Contributions that cross these boundaries will be rejected.
