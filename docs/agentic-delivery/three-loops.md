# Three Loops

The process improves itself through three loops of different length. Each measures something the shorter one cannot see,
and each changes something the shorter one may not touch.

| Loop   | Runs                    | Measures                                                                                    | Changes                                                                           |
| ------ | ----------------------- | ------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Inner  | Within one deliver run  | Review rounds and feedback items                                                            | The change itself, until the reviewer is satisfied                                |
| Middle | After every deliver run | Rounds, estimate against actual effort, token use                                           | The harness, through the Harness Gate and checked by the baseline delivery        |
| Outer  | Over the whole project  | The DevOps Research and Assessment (DORA) metrics, the token ledger against the team's cost | How the process is run, for example moving well-specified steps to cheaper models |

Fewer rounds in the inner loop are the sign that specification and plan got better; the middle loop is where that is
made to happen, and the outer loop shows whether it pays off. Next to the loops, the Houseclean skill keeps the work
items in line with the code, so specification and planning start from facts that are still true.

## What Each Loop Needs

- The inner loop needs the reviewer's verdict to be structured: one feedback item per finding, addressed to the agent or
  person who can act on it, so the next round fixes the finding and nothing else.
- The middle loop needs every run to leave the same measurements behind, in the commit trailers and the token ledger,
  and a fixed baseline delivery to run a proposed change against. Without the baseline, an improvement is a claim.
- The outer loop needs the pipeline and the environments to report their events, and the ledger to be complete enough
  for a forecast. It is the only loop that may change the bet of the [Business Case](the-case.md#business-case), such as
  which model a step runs on, and it does so on the ledger, never on a feeling about a model.

## Removing Is a Change Too

A retrospective that only adds rules grows the harness until the agents stop following it. A proposal to remove an
instruction, a check or a step is as valid as a proposal to add one, and is measured the same way: against the baseline
delivery, on time, quality or cost.
