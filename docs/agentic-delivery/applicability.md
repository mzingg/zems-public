# Applicability

The process does not fit every kind of work. It pays off where written specifications, tests and reviews carry the work,
and adds overhead where human decisions dominate, whatever the models cost.

| Use case                                 | Fit     | Description                                                                                                                                             |
| ---------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Product development over months          | Best    | A stable team works on a code base that grows over time; the harness and its improvements pay off with every delivery                                   |
| Client delivery with audit needs         | Good    | Requirements, tests, decisions and commits are traceable in the repository, which eases audits, handover and change of staff                            |
| Well-specified features and integrations | Good    | The outcome can be written down and tested, so agents can deliver it with few human rounds                                                              |
| Prototypes and spikes                    | Partial | Agents are fast here, but the full process is overhead; use agents with a lighter way of working and switch to the full process once the result is kept |
| Small one-off changes                    | Poor    | Specification, plan and review take longer than the change itself                                                                                       |
| Stakeholder alignment and discovery      | Poor    | The work is mostly human decisions and conversations, so there is little for agents to deliver                                                          |

In every case, the organisation must allow frontier models and agentic CLI tools on developer machines, and for client
work the client must agree that its code and data may go to the model provider.

## Delivery Assumptions

The process also assumes a way of delivering that suits many small, agent-made changes:

- **Monorepo:** the solution, its configuration, the harness and the project memory live in one repository, so agents
  see the whole context and one commit can change everything a work item needs
- **Trunk-based:** every delivery is committed to the main branch in small steps, with no long-lived feature branches to
  merge later
- **Feature-flag driven where users would see it:** unfinished or unreleased user-facing work is merged behind a flag,
  so committing to the main branch and releasing to users are separate decisions; everything else is merged as an
  additive change (see [Feature Flag Management](environments.md#feature-flag-management))

A project that cannot work this way can still use the harness, but loses part of the speed and safety the process is
built for.
