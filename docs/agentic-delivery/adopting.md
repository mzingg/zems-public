# Adopting the Model

The essay describes the whole model. A team adopting it does not need every block on day one: the core is what makes the
process safe and measurable, the optional blocks pay off with scale and stakeholders, and several blocks are target
state that an implementation grows into. Two projects can therefore both follow the model and still look different; what
they share is the pattern, checked by the list at the end of this page.

## Core and Optional Blocks

| Block                                                                                                 | Core or optional | Why                                                                                                              |
| ----------------------------------------------------------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------------------------------------- |
| Work items as files, with a state and acceptance criteria that carry IDs                              | Core             | Without them nothing can be specified, pinned by a test or traced                                                |
| The skills that follow a work item: capture, triage, specify, plan, deliver, houseclean               | Core             | They are the process; a step without a written playbook drifts with every session                                |
| Test, Code, Plan Critic, Reviewer and Retrospective agents with separation of duties                  | Core             | The separation is what keeps plausible but wrong output from reviewing itself                                    |
| Tests before code, a critic before plan approval, a reviewer verdict before the commit                | Core             | The three places where the process controls itself                                                               |
| A commit message check with machine-readable trailers, and the human's permission for every commit    | Core             | The git log is the logbook and the audit trail; the permission keeps the human in the loop                       |
| Private agent memory off, secret files denied, the project context in the repository                  | Core             | Shared knowledge and secure defaults cost nothing and are hard to add later                                      |
| A retrospective after every delivery, with proposals the human picks                                  | Core             | Without it the process cannot improve on evidence                                                                |
| Decision Proxy Agent with curated precedents in the repository                                        | Optional         | Pays off once the human's answers repeat; needs the retrospective to maintain the precedents                     |
| Tool map, agent smoke test and baseline delivery                                                      | Optional         | Needed as soon as a second agentic tool is used, or as soon as harness changes come often enough to break things |
| Token ledger and forecast                                                                             | Optional         | Needed as soon as cost is reported to someone; the trailers carry the rounds and estimates until then            |
| Cockpit and external state sync                                                                       | Optional         | Needed as soon as stakeholders live in other tools                                                               |
| Full local commit gate (formatter, linter, tests in a hook), hygiene and release gates                | Optional         | Each check can be run by hand first; the hook makes it unavoidable                                               |
| Delivery pipeline, Preview and Integration environments, deployment history, DORA metrics from events | Target state     | Needs infrastructure that a project builds up over time; until then the Integration Gate runs by hand            |
| Flag service with audit log                                                                           | Target state     | Needs a flag service and a product that users see; a library or an internal tool may never need it               |

## Variation Points

| Varies by implementation                                  | Fixed by the model                                                                                    |
| --------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| The agentic CLI tool, and which one is the reference      | Skills and agents are written tool-neutral; the map translates                                        |
| The shape of the work item files and the tracker's states | State is machine-readable; every acceptance criterion has an ID a test pins                           |
| The model and the effort each agent runs on               | Critic, reviewer and retrospective never run on a weaker model than the agents they judge             |
| The trailer keys and the ledger format                    | Every delivery commit carries the issue moved, the estimate, the rounds and the evidence              |
| The pipeline, the environments and the flag service       | Build once, deploy everywhere, release user-facing change by flag, every flag change a human decision |
| How the plan is kept                                      | The plan is approved by the human after the critic, and its decisions reach the repository            |
| What the deterministic checks are                         | A fact is verified by a script, never by an LLM                                                       |

## Conformance Checklist

An implementation follows the model when it can answer yes to each question, and name the file or the check that makes
it true.

1. Does every work item live in the repository, with its state readable by a script and every acceptance criterion
   carrying an ID?
2. Does a test pin every acceptance criterion, and does a check fail when the link is missing in either direction?
3. Does a critic attack every plan before the human approves it, and does the human see the findings and their fate?
4. Do the tests exist, and fail, before the production code is written?
5. Does the agent that writes tests never touch production code, and the agent that writes code never edit tests?
6. Does a reviewer that cannot edit return a verdict with one feedback item per finding before any commit?
7. Does every commit need the human's explicit permission, and does only the delivery lead commit?
8. Does a hook check every commit message, and does it refuse an agent attribution?
9. Does every delivery leave its rounds, estimate and evidence in the repository, in a form a script can read?
10. Does a retrospective follow every delivery, and is "no change" a recorded outcome?
11. Is every harness change tested on the same baseline work before it is in use?
12. Is private agent memory off, and is every secret file denied to the agents?
13. Does every manual verification ask the human for an observation, never for a plain approval?
14. Is every production deployment and every flag change a human decision, with agents only building against flags and
    never switching one?
15. Does a change get a flag only where users would see it, named in the story or the issue?
