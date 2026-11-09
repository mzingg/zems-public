# Business and Craft Case

## Business Case

The main cost of a delivery process is the team: its people cost the same every month, however their hours are spent.
Model tokens, in contrast, are a small variable cost on top. Imagine how many autonomous development iterations you can
buy for what one senior engineer costs per day.

The goal of this process is therefore to get as much delivered output as possible out of a team's fixed cost: every
human interaction should be impactful and short, and the delivery should match the specification in the first iteration.

The business case bet is that deliberately using the most capable models, like Fable, will be more cost-effective than
having more and lower-quality iterations with cheaper or local models. Also we use the same, very capable models, to
self-improve, capture the project stakeholders' vision and improve the harness over time. Costs are optimised only once
quality and effectiveness hold, for example by moving well-specified steps to cheaper models, never before.

```mermaid
flowchart TB
  subgraph cheap[Cheaper or local model]
    b1[Specification] --> b2["Iteration: fewer tokens"] --> b3(["Human review: minutes"])
    b3 -->|not yet| b4["Iteration: fewer tokens"] --> b5(["Human review: minutes"])
    b5 -->|not yet| b6["Iteration: fewer tokens"] --> b7(["Human review: minutes"]) --> b8[Matches the specification]
  end
  subgraph capable[Most capable model]
    a1[Specification] --> a2["Iteration: more tokens"] --> a3(["Human review: minutes"]) --> a4[Matches the specification]
  end
```

Because a team's cost is fixed, the success of the process (and proof that the bet is won) can only be measured using
already established metrics, for example with the DevOps Research and Assessment (DORA) metrics (deployment frequency,
lead time for changes, change failure rate and time to restore service), before and after the process is introduced and
continuously tracking them over the whole life of a project.

The risks are rising frontier model prices and vendor lock-in. To mitigate these, we design the harness in a way that it
supports many CLI agentic tools. This ensures that we do not have vendor lock-in on the tooling level (where it hurts
the most) and that a pivot to different model providers is possible.

```mermaid
flowchart TB
  subgraph fixed[Stays as it is]
    process["The process: skills, agents and instructions,<br>written in tool-neutral terms"]
  end
  subgraph tools[Swappable: the agentic CLI tool]
    map["Tool map: one column per tool"] --> t3[Tool C] & t2[Tool B] & t1[Tool A]
  end
  subgraph providers[Swappable: the model provider]
    p1[Provider X]
    p2[Provider Y]
  end
  process --> map
  tools --> providers
```

## Craft Case

We find that agentic delivery gives us the opportunity to 'do it right'. Many practices that in the past were skipped or
compromised on (Test-Driven Development anybody?) can now be automated and form naturally with the support of the
agentic process.

They were skipped because they cost human discipline and time. In an agentic process they become convenient: the process
treats them as first-class citizens and enforces them. Agents also need them more than humans do: tests and
deterministic checks are what stop plausible but wrong output from getting through. Best practices are therefore no
longer optional quality work but the way the process controls itself, for example:

- acceptance criteria pinned by tests, written before the code
- a plan that is reviewed before any code is written
- an independent review of every change
- documentation, decisions and commit messages kept in one agreed form

```mermaid
flowchart LR
  subgraph practice[The practice]
    p1["Acceptance criteria pinned by tests,<br>written before the code"]
    p2["A plan reviewed before<br>any code is written"]
    p3["An independent review<br>of every change"]
    p4["Documentation, decisions and commit<br>messages kept in one agreed form"]
  end
  subgraph enforcer[What makes it unavoidable]
    e1["Test Agent, red first;<br>acceptance coverage check"]
    e2["Plan Critic Agent;<br>Plan Gate"]
    e3["Reviewer Agent, reads only;<br>Commit Gate"]
    e4["Work item linter, schema validation,<br>commit message check"]
  end
  p1 --> e1
  p2 --> e2
  p3 --> e3
  p4 --> e4
```

Because requirements, tests, decisions and commits all live in the repository and point to each other, every change can
be traced from requirement to commit. This makes the work auditable, eases handover and lowers the dependency on single
persons.

To measure success from the technical point of view, the DORA metrics (see above) are suited as well, as they also prove
the technical maturity of the team and its deliverables.

The risks are that agents produce more code than humans can review in a meaningful way and approving without reading
becomes tempting, with the consequence that the team's knowledge erodes. To mitigate this, work is cut into small items,
facts are checked by deterministic scripts, and human verification steps are designed so that just accepting is hard:
control questions that check the change was read, inverse questions where the accepting answer stops the process, and
tasks that ask for specific feedback instead of a plain approval.

```mermaid
flowchart TB
  output["Agent output: more code than<br>a human can read in full"] --> small["Cut into small work items"]
  small --> checks["Deterministic checks take the mechanical part:<br>format, types, tests, coverage, agreed forms"]
  checks -->|what remains to read| human(["Human verification"])
  human --> q1["Control questions that<br>show the change was read"]
  human --> q2["Inverse questions where the<br>accepting answer stops the process"]
  human --> q3["Tasks that ask for specific<br>feedback, not a plain approval"]
  q1 & q2 & q3 --> accepted["Accepted, with the<br>team's knowledge kept"]
```
