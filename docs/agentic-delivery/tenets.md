# Tenets and Guardrails

Six tenets set the direction. Each comes with the guardrails that keep an implementation on it; the guardrails are the
rules a [conformance check](adopting.md#conformance-checklist) asks about.

## Humans steer, agents deliver

- The human specifies with the support of an LLM; the LLM orchestrates the delivery
- The LLM makes decisions where precedent and high confidence are given; the human decides where precedent is not given
  or confidence is low
- Human verification needs active involvement, not just approval (see [Craft Case](the-case.md#craft-case))
- Escalations go to the human, never around the loop; a change to the requirement or the plan goes back through
  specification
- Agents work only inside the repository; anything in an outside system, publishing to production and pushing are human
  steps, and every commit needs explicit human permission
- Every production deployment and every flag change is a human decision or an environment's standing rule; agents build
  against the flag a story or issue names and never switch one, in any environment

## Quality and security by design

- Best practices are primary building blocks of the process; do not compromise on quality, both on the delivery and on
  the process
- Plan before code: every delivery starts from a written plan that a critic agent attacks and the human approves before
  any implementation starts
- Build once: one artefact per change is promoted through every environment. A faulty feature is switched off by its
  flag where it has one, otherwise fixed forward by a new delivery; rolling back a deployment, with one documented step,
  is kept for catastrophic failures only
- Flags sparingly: a change gets a flag only where users would see it and someone must be able to switch it; the story
  or the issue names the flag, the tests run with it on and off, and a fully released flag is removed by a follow-up
  work item. Everything else ships without one, as an additive change or behind a versioned API
- Secure by design: agents are denied read access to secret files, secrets are never committed, and dev tooling is
  switched on explicitly, saying what it exposes in the environment

## Focused agents with least rights

- Subagents keep the context small, let each step run on the model it needs and reduce hallucination and bias
- Separation of duties: every agent has one role and only the rights it needs (the test writer never touches production
  code, the coder never edits tests, critic and reviewer only read); only the delivery lead commits
- Pointers, not prose: the delivery lead hands agents file paths and feedback items, never its own summary of the
  requirement; the files are the source, the delivery lead is a lossy middleman

## Verify by facts, improve on evidence

- Never use an LLM to test or verify a fact; write a deterministic script whenever a fact is verified
- Get better over time (higher quality, fewer rounds, more cost-effective, more autonomous): a retrospective follows
  every delivery, and the process changes only on evidenced impact on time, quality or cost; "no change" is a valid
  outcome
- Every process change is checked against a fixed baseline delivery, so improvements are measured on the same work and
  not only claimed
- Every delivery is measured against the estimate it started from; a wrong estimate is a finding for the retrospective,
  not an edit

## Git is the single source of truth

- For the solution and for the process metrics (number of rounds, token cost, decisions)
- Requirements live as files, with each acceptance criterion linked to a test and the link checked both ways
- Commit messages follow a checked form with machine-readable trailers, so the git log is the project's logbook
- No private agent memory: durable knowledge goes into the repository, where every collaborator sees it

## Portable across tools

- CLI first: the process runs on the developer machine, which is easier to provide and leaves the door open for local
  models
- The harness is written for more than one agentic CLI tool: skills and agents name operations in tool-neutral terms,
  and one map per tool translates them
- Where a tool cannot do what the reference tool does, the gap is named; the reference path is never weakened to match
