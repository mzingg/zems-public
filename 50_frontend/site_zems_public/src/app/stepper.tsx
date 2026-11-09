'use client';

import {useState} from 'react';
import type {Journey} from '@/docs-source/blocks';

export function Stepper({journey}: {journey: Journey}) {
  const [index, setIndex] = useState(0);
  const {states, gates} = journey;
  const last = states.length - 1;
  const gate = gates[index];
  return (
    <div className="stepper" data-testid="journey-stepper">
      <ol className="stepper-states">
        {states.map((state, position) => (
          <li key={state}>
            <button
              type="button"
              aria-current={position === index ? 'step' : undefined}
              onClick={() => setIndex(position)}
            >
              {state}
            </button>
          </li>
        ))}
      </ol>
      <div className="stepper-gate" aria-live="polite">
        {gate ? (
          <>
            <p>
              From <strong>{states[index]}</strong> to <strong>{states[index + 1]}</strong> through the{' '}
              <strong>{gate.name}</strong>
            </p>
            <dl>
              <dt>Checks</dt>
              <dd>{gate.checks}</dd>
              <dt>Human decision</dt>
              <dd>{gate.decision}</dd>
            </dl>
          </>
        ) : (
          <p>
            <strong>{states[index]}</strong> is the last stage: the work item has passed every gate.
          </p>
        )}
      </div>
      <div className="stepper-controls">
        <button type="button" className="secondary" disabled={index === 0} onClick={() => setIndex(index - 1)}>
          Back
        </button>
        <button type="button" disabled={index === last} onClick={() => setIndex(index + 1)}>
          {gate ? `Pass the ${gate.name}` : 'Done'}
        </button>
      </div>
    </div>
  );
}
