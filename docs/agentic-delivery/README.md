# Self-Reinforcing Agentic Delivery

An essay on how a software delivery process run by coding agents could improve itself: humans steer, agents deliver,
deterministic checks verify the facts, and every delivery ends in a retrospective that may change the process for the
next one.

This is a model, not a report. Nothing here claims that a team runs the process this way or that every building block
exists somewhere; where an implementation has a choice, the text names the choice. The essay is written as a pattern, so
that a team can adopt the core, pick the optional blocks it needs and check its own harness against the model.

## The Idea in Short

- A team's cost is fixed; model tokens are a small variable cost on top. The process tries to turn as much of that fixed
  cost as possible into delivered output, with few and short human interactions.
- The practices that were skipped because they cost human discipline, such as tests before code, a plan that is reviewed
  and an independent review of every change, become the way the process controls itself.
- Everything the project knows lives in its repository. Requirements, tests, decisions, metrics and commits point to
  each other, so every change can be traced and the process can be measured.
- A retrospective follows every delivery and changes the process only on evidence. Three loops of different length make
  the improvement measurable.

## Reading Order

1. [Business and Craft Case](the-case.md) — why the process is worth building, for the budget and for the craft
2. [Applicability](applicability.md) — where it pays off and where it adds overhead
3. [Tenets and Guardrails](tenets.md) — the design goals and the rules that keep them
4. [Building Blocks](building-blocks.md) — personas, harness, project memory, external state and cockpit
5. [Checks and Gates](checks-and-gates.md) — the deterministic checks, what a gate is and how it is enforced
6. [Environments and Pipeline](environments.md) — the developer machine, the pipeline, the target environments and the
   feature flags
7. [The Life of a Work Item](process.md) — the states, the gates between them and the stages from an idea to a feature
   users see
8. [The Deliver Run](deliver-run.md) — plan, build rounds, verification, review, commit and retrospective
9. [Three Loops](three-loops.md) — how the process improves itself
10. [Adopting the Model](adopting.md) — core and optional blocks, variation points and a conformance checklist
