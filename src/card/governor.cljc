(ns card.governor
  "Card Settlement Governor -- the independent compliance layer that
  earns the Card Advisor the right to commit. The LLM has no notion of
  jurisdictional card-settlement/chargeback-dispute law, whether a
  transaction's own settlement amount actually stays within its own
  authorized amount, whether a fraud flag against the transaction has
  actually stayed unresolved, or when an act stops being a draft and
  becomes a real-world settlement finalization or chargeback-hold
  release, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD -- the card-processing analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, a
  settlement amount over-charging the granted authorization, an
  unresolved fraud flag, or a double settlement/chargeback-release).
  The confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `card.phase`: for `:stake :actuation/settle-transaction`/`:actuation/
  release-chargeback` (a real financial act) NO phase ever allows
  auto-commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`card.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:settlement/finalize`/
                                       `:chargeback/release`, has the
                                       jurisdiction actually been
                                       assessed with a full transaction-
                                       intake/authorization-disclosure/
                                       settlement-ledger/chargeback-
                                       procedure evidence checklist on
                                       file?
    3. Settlement amount exceeds
       authorized amount             -- for `:settlement/finalize`,
                                       INDEPENDENTLY recompute whether
                                       the transaction's own settlement
                                       amount exceeds its own
                                       authorized amount (`card.
                                       registry/settlement-amount-
                                       exceeds-authorized?`) -- needs
                                       no proposal inspection or
                                       stored-verdict lookup at all.
                                       The THIRD non-temporal instance
                                       of this fleet's MAXIMUM-ceiling
                                       check family (`facility.
                                       governor/occupancy-exceeds-
                                       capacity-violations`/`school.
                                       governor/class-size-exceeds-
                                       maximum-violations` established
                                       the first two), directly
                                       implementing this blueprint's
                                       own 'partial approvals never
                                       over-charge the granted amount'
                                       Trust Control.
    4. Fraud flag unresolved       -- reported by THIS proposal itself
                                       (a `:fraud/screen` that just
                                       found an unresolved fraud flag),
                                       or already on file for the
                                       transaction (`:fraud/screen`/
                                       `:chargeback/release`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       ...(twenty-three prior
                                       siblings)... established -- the
                                       TWENTY-FOURTH distinct
                                       application of this exact
                                       discipline, and the FIRST
                                       specifically for a fraud-flag
                                       concept. Like the thirteen most
                                       recent siblings' equivalent
                                       checks, this is exercised in
                                       tests/demo via `:fraud/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened
                                       transaction -- see this ns's own
                                       test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:settlement/
                                       finalize`/`:chargeback/release`
                                       (REAL financial acts) ->
                                       escalate.

  Two more guards, double-settlement/double-release prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-settled-violations`/
  `already-released-violations` refuse to settle a transaction/
  release a chargeback hold for the SAME transaction twice, off
  dedicated `:settled?`/`:chargeback-released?` facts (never a
  `:status` value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [card.facts :as facts]
            [card.registry :as registry]
            [card.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Finalizing a real settlement and releasing a real chargeback hold
  are the two real-world actuation events this actor performs -- a
  two-member set, matching every prior dual-actuation sibling's
  shape."
  #{:actuation/settle-transaction :actuation/release-chargeback})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:settlement/finalize`/`:chargeback/
  release`) proposal with no spec-basis citation is a HARD violation
  -- never invent a jurisdiction's card-settlement/chargeback-dispute
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :settlement/finalize :chargeback/release} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:settlement/finalize`/`:chargeback/release`, the jurisdiction's
  required transaction-intake/authorization-disclosure/settlement-
  ledger/chargeback-procedure evidence must actually be satisfied --
  do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:settlement/finalize :chargeback/release} op)
    (let [t (store/transaction st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction t) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(取引受付記録/オーソリゼーション開示書/決済元帳証明書/チャージバック処理手続き文書等)が充足していない状態での提案"}]))))

(defn- settlement-amount-exceeds-authorized-violations
  "For `:settlement/finalize`, INDEPENDENTLY recompute whether the
  transaction's own settlement amount exceeds its own authorized
  amount via `card.registry/settlement-amount-exceeds-authorized?` --
  needs no proposal inspection or stored-verdict lookup at all, since
  its input is a permanent ground-truth field already on the
  transaction."
  [{:keys [op subject]} st]
  (when (= op :settlement/finalize)
    (let [t (store/transaction st subject)]
      (when (registry/settlement-amount-exceeds-authorized? t)
        [{:rule :settlement-amount-exceeds-authorized
          :detail (str subject " の決済金額(" (:settlement-amount t)
                      ")がオーソリ金額(" (:authorized-amount t) ")を超過")}]))))

(defn- fraud-flag-unresolved-violations
  "An unresolved fraud flag -- reported by THIS proposal (e.g. a
  `:fraud/screen` that itself just found one), or already on file in
  the store for the transaction (`:fraud/screen`/`:chargeback/
  release`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        transaction-id (when (contains? #{:fraud/screen :chargeback/release} op) subject)
        hit-on-file? (and transaction-id (= :unresolved (:verdict (store/fraud-screen-of st transaction-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :fraud-flag-unresolved
        :detail "未解決の不正利用フラグがある状態でのチャージバック解除提案は進められない"}])))

(defn- already-settled-violations
  "For `:settlement/finalize`, refuses to settle the SAME transaction
  twice, off a dedicated `:settled?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :settlement/finalize)
    (when (store/transaction-already-settled? st subject)
      [{:rule :already-settled
        :detail (str subject " は既に決済済み")}])))

(defn- already-released-violations
  "For `:chargeback/release`, refuses to release a chargeback hold for
  the SAME transaction twice, off a dedicated `:chargeback-released?`
  fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :chargeback/release)
    (when (store/transaction-already-released? st subject)
      [{:rule :already-released
        :detail (str subject " は既にチャージバック解除済み")}])))

(defn check
  "Censors a Card Advisor proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (settlement-amount-exceeds-authorized-violations request st)
                           (fraud-flag-unresolved-violations request proposal st)
                           (already-settled-violations request st)
                           (already-released-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
