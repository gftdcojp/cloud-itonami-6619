# ADR-0001: cloud-itonami-isic-6619 -- Card Advisor as a contained intelligence node

- Status: Accepted (2026-07-08)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311`/`8510`/`9412`/`6491`/`8720`/`8521` ADR-0001s (the
  pattern this ADR ports); ADR-2607071250/ADR-2607071320/
  ADR-2607071351/ADR-2607071618/ADR-2607071640/ADR-2607071654/
  ADR-2607071717/ADR-2607071732/ADR-2607071752/ADR-2607071819/
  ADR-2607071849/ADR-2607071922/ADR-2607072715/ADR-2607072730/
  ADR-2607072745/ADR-2607072800/ADR-2607072815/ADR-2607072830/
  ADR-2607072845/ADR-2607072900/ADR-2607072915/ADR-2607080100/
  ADR-2607080200/ADR-2607080300/ADR-2607080400 (`6612`/`6492`/`6920`/
  `6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
  `8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/
  `9412`/`6491`/`8720`/`8521`, the twenty-five verticals built outside
  ADR-2607032000's original insurance/real-estate batch -- this is
  the twenty-sixth)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `8521`, this ADR deepens `cloud-itonami-
  isic-6619` (card transaction processing and settlement) from
  `:blueprint` to `:implemented`, the fortieth actor in this fleet --
  a THIRD financial-services vertical alongside `6492`'s credit
  granting and `6491`'s financial leasing, but for card-payment
  processing/settlement rather than lending.

## Problem

A card processor's settlement-finalization/chargeback-release
workflow bundles several distinct concerns under one governed
workflow:

1. **Jurisdiction card-settlement/chargeback-dispute correctness** --
   an official spec-basis citation from a real regulator (経済産業省
   under the Installment Sales Act's card-data-handling registration
   regime/the CFPB's Regulation Z/the FCA's PSD2 transposition/BaFin
   under the ZAG), never fabricated.
2. **Settlement-amount sufficiency** -- does a transaction's own
   settlement amount stay within its own authorized amount? The THIRD
   non-temporal instance of this fleet's MAXIMUM-ceiling check family
   (`facility.registry/occupancy-exceeds-capacity?`/`school.registry/
   class-size-exceeds-maximum?` established the first two), directly
   implementing this blueprint's own stated Trust Control: "partial
   approvals never over-charge the granted amount."
3. **Fraud-flag resolution verification** -- has a fraud flag against
   the transaction actually stayed unresolved before a chargeback hold
   is released? The card-processing-specific application of the
   unconditional-evaluation screening discipline this fleet's
   `casualty.governor/sanctions-violations` originally established --
   a TWENTY-FOURTH distinct grounding overall, and the FIRST
   specifically for a fraud-flag concept.
4. **Real, high-stakes actuation, twice** -- finalizing a real
   settlement and releasing a real chargeback hold are two
   independently-gated real-world financial acts on the SAME entity (a
   transaction).

An LLM has no authority or grounding for any of these -- and critically,
it must NEVER handle a raw PAN (Primary Account Number) at all. The
design problem is therefore not "run a card processor with an LLM" but
"seal the LLM inside a trust boundary that never touches a raw PAN,
and layer evidence-sufficiency, settlement-amount verification, fraud-
flag-resolution verification, audit and human-approval on top of it,
while structurally fixing both real actuation events as human-only."

## Decision

### 1. Card Advisor is sealed into the bottom node; it never finalizes a settlement or releases a chargeback hold directly, and never handles a raw PAN

`card.cardadvisor` returns exactly five kinds of proposal: intake
normalization, jurisdiction card-settlement checklist, fraud
screening, settlement-finalization draft, and chargeback-release
draft. No proposal writes the SSoT or commits a real settlement/
chargeback-release directly, and no field in this actor's schema (see
`card.store`'s own docstring) ever carries a raw PAN -- tokenization
at the edge is explicitly out of scope, the responsibility of the
related `kotoba-lang/card` capability contract, not this actor.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 card-processing operation

`card.operation/build` is the SAME StateGraph shape as every sibling
actor's operation namespace, copied verbatim.

### 3. `settlement-amount-exceeds-authorized?` is the THIRD non-temporal instance of the MAXIMUM-ceiling check family

`facility.registry/occupancy-exceeds-capacity?` established the FIRST
non-temporal check in this fleet's MAXIMUM-ceiling family, `school.
registry/class-size-exceeds-maximum?` the SECOND. `settlement-amount-
exceeds-authorized?` is the THIRD instance, comparing a transaction's
own settlement amount against its own authorized amount -- and unlike
every prior check in this build sequence, it was derived DIRECTLY from
this blueprint's own explicitly published Trust Control ("partial
approvals never over-charge the granted amount"), rather than an
invented domain concern, giving this check an unusually direct textual
grounding in the blueprint itself.

### 4. Fraud-flag screening reuses the unconditional-evaluation discipline for a twenty-fourth distinct grounding, and a first for this concept

`fraud-flag-unresolved-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for `:fraud/screen` AND `:chargeback/release` -- the TWENTY-
FOURTH distinct application of this exact discipline in this fleet
overall, and the FIRST specifically for a fraud-flag concept. This
check gates `:chargeback/release` (not `:settlement/finalize`) since
an unresolved fraud investigation is most directly relevant to
releasing a hold on disputed funds, the natural point in this
workflow where a live fraud concern must be cleared first.

### 5. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson already recorded by `parksafety` and thirteen later siblings

`fraud-flag-is-held-and-unoverridable` calls `:fraud/screen` directly
against `transaction-4` (an unresolved fraud flag), NOT `:chargeback/
release` against an unscreened transaction -- because a failing screen
is itself a HARD hold whose payload never persists to the store, so
the actuation op alone could never discover the bad ground-truth flag
through this check family without the screening op having actually
been run first. This build applied that lesson PROACTIVELY for a
fourteenth consecutive vertical (after `eldercare`, `museum`,
`conservation`, `salon`, `entertainment`, `casework`, `hospital`,
`facility`, `school`, `association`, `leasing`, `behavioral` and
`secondary`), further reinforcing that lessons recorded in this
fleet's ADRs transfer forward reliably.

### 6. Dual actuation, matching `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/`8720`/`8521`'s shape

`card.governor`'s `high-stakes` set has exactly two members
(`:actuation/settle-transaction`, `:actuation/release-chargeback`),
each acting on the SAME transaction entity, each with its OWN history
collection (`settlement-history`/`chargeback-history`), sequence
counter and dedicated double-actuation-guard boolean.

### 7. Double-settlement/double-release guards check dedicated booleans, not `:status`

`already-settled-violations`/`already-released-violations` check
`:settled?`/`:chargeback-released?`, dedicated booleans set once and
never cleared, rather than a `:status` value that could legitimately
advance past a checked state (the exact trap `cloud-itonami-isic-
6492`'s ADR-0001 documents in detail, explicitly avoided BY DESIGN in
every sibling actor's equivalent guard since). This actor's `:status`
never needs to encode "has this actuation already happened" at all --
a deliberate architectural choice applied here for a twenty-fifth
consecutive time.

### 8. Related capability contracts cited, but not directly required (matching `6492`'s/`6491`'s posture)

Like `credit.*` (`6492`) and `leasing.*` (`6491`), this actor's
`card.*` namespaces cite [`kotoba-lang/card`](https://github.com/kotoba-lang/card),
[`kotoba-lang/banking`](https://github.com/kotoba-lang/banking) and
[`kotoba-lang/swift`](https://github.com/kotoba-lang/swift) as related
capability contracts for PAN/Luhn/ISO-8583 validation, clearing-batch/
settlement-ledger shapes and interbank settlement messaging
respectively, but do not require any of them directly -- `card.*` is a
self-contained governed implementation, the same "self-contained
sibling" relationship every prior actor with a related capability
contract maintains. This is the FIRST actor in this fleet to cite
THREE related capability contracts at once (every prior instance cited
at most one), reflecting this blueprint's own genuinely broader
`:required-technologies` list.

### 9. Blueprint's own internal ID field corrected to match the renamed repo

This blueprint's `blueprint.edn` still carried the pre-rename
`:itonami.blueprint/id "cloud-itonami-6619"` even though the GitHub
repo itself, and the industry registry's own `:business-id`, were both
already `cloud-itonami-isic-6619` -- a leftover inconsistency from the
"ISIC taxonomy-prefix normalization" rename this fleet's repos went
through. Fixed as part of this promotion to `cloud-itonami-isic-6619`,
matching every other reference to this actor.

## Consequences

- (+) Card transaction processing/settlement gets the same governed,
  auditable-actor treatment as the thirty-three prior actors, and this
  fleet now has a TWENTY-SIXTH concrete precedent for extending past
  ADR-2607032000's original scope, deepening financial-services
  coverage alongside `6492`'s credit granting and `6491`'s financial
  leasing with a genuinely different financial-services model (card-
  payment settlement vs. lending).
- (+) `settlement-amount-exceeds-authorized?` is a genuine structural
  contribution: the third non-temporal instance of the MAXIMUM-ceiling
  family, with an unusually direct textual grounding in this
  blueprint's own published Trust Controls.
- (+) `fraud-flag-unresolved-violations` is a genuine domain-modeling
  contribution: the first unconditional-evaluation grounding for a
  fraud-flag concept.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/card/phase_test.clj`'s `settlement-
  finalize-never-auto-at-any-phase`/`chargeback-release-never-auto-
  at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/card/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses, with NO raw-PAN field anywhere in the
  schema by design.
- (+) The fraud-flag test/demo correctly applied the established
  SCREENING-op-directly pattern for a fourteenth consecutive vertical
  -- further evidence that lessons recorded in this fleet's ADRs
  continue to transfer forward reliably.
- (+) The blueprint's own stale internal ID field was corrected to
  match its renamed repo and registry business-id, a small but genuine
  consistency fix.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `card.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) `settlement-amount-exceeds-authorized?` models only a single
  amount-comparison concern, not PAN/Luhn validation, ISO 8583 message
  handling, or a full card-network integration -- those remain the
  related capability contracts' and the operator's own responsibility
  (see README `Scope`/coverage table for the full honest-scope
  accounting).
- 36 tests / 173 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to `cloud-itonami-isic-6492`'s or `6491`'s ADR | ❌ | Both of those ADRs' titles and scopes are explicitly credit granting/financial leasing; card-payment processing/settlement is a distinct financial-services sub-domain with its own actuation shape, even though the broad financial-services sector overlaps |
| Keep `cloud-itonami-isic-6619` at `:blueprint` only | ❌ | The standing direction continues past `8521`; card transaction processing is a natural, well-precedented next domain, deepening this fleet's financial-services coverage alongside `6492`'s credit granting and `6491`'s financial leasing |
| Actually integrate `kotoba-lang/card`/`banking`/`swift` as code dependencies | ❌ | `credit.governor` (`6492`) and `leasing.governor` (`6491`), the closest siblings with related capability-tech tags, both treat them as cited-but-not-required related capability contracts, not code dependencies -- matching that established posture keeps this actor self-contained like every other sibling |
| Store a raw PAN (even tokenized-in-appearance) on the transaction entity, for realism | ❌ | The blueprint's own Trust Controls are explicit: "raw PANs are never persisted — tokenization is mandatory at the edge." Modeling even a placeholder PAN field would misrepresent this actor's own scope boundary; the entity carries only a `merchant-name` and amount/flag fields, no card-data field of any kind |
| Test `fraud-flag-unresolved-violations` via an actuation op against an unscreened transaction (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s own ADR-2607071922 Decision 5 and reconfirmed by thirteen later siblings' ADR-0001s -- a failing screen never persists its payload to the store, so the actuation op alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Leave the blueprint's stale `cloud-itonami-6619` internal ID field untouched | ❌ | It contradicts both the renamed GitHub repo and the industry registry's own `:business-id "cloud-itonami-isic-6619"` -- a small, low-risk, in-scope consistency fix to make during this promotion |

## References

- ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915/ADR-2607080100/ADR-2607080200/ADR-2607080300/
  ADR-2607080400 (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/
  `9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/
  `9000`/`8890`/`8610`/`9311`/`8510`/`9412`/`6491`/`8720`/`8521`,
  first twenty-five post-batch verticals)
- ADR-2607032000 (original insurance/real-estate batch, Addenda 1-7)
- `cloud-itonami-isic-6619/docs/adr/0001-architecture.md` (this ADR)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn`
  (fleet-wide maturity registry)
