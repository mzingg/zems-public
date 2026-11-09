import {fireEvent, render, screen} from '@testing-library/react';
import '@testing-library/jest-dom';
import {Stepper} from '@/app/stepper';
import type {Journey} from '@/docs-source/blocks';

const JOURNEY: Journey = {
  states: ['Idea', 'Backlog', 'Open'],
  gates: [
    {name: 'Jot Gate', checks: 'none', decision: 'none'},
    {name: 'Specification Gate', checks: 'work item linter', decision: 'the owner accepts'},
  ],
};

describe('Stepper', () => {
  beforeEach(() => {
    render(<Stepper journey={JOURNEY} />);
  });

  it('starts at the first state and shows the gate ahead of it', () => {
    const stepper = screen.getByTestId('journey-stepper');

    expect(screen.getByRole('button', {name: 'Idea'})).toHaveAttribute('aria-current', 'step');
    expect(stepper).toHaveTextContent('From Idea to Backlog through the Jot Gate');
    expect(screen.getByRole('button', {name: 'Back'})).toBeDisabled();
  });

  it('passes the gate ahead on the next button and shows the following gate', () => {
    fireEvent.click(screen.getByRole('button', {name: 'Pass the Jot Gate'}));

    expect(screen.getByRole('button', {name: 'Backlog'})).toHaveAttribute('aria-current', 'step');
    expect(screen.getByTestId('journey-stepper')).toHaveTextContent('work item linter');
    expect(screen.getByTestId('journey-stepper')).toHaveTextContent('the owner accepts');
  });

  it('jumps to a state from its button and says when the last one is reached', () => {
    fireEvent.click(screen.getByRole('button', {name: 'Open'}));

    expect(screen.getByTestId('journey-stepper')).toHaveTextContent('Open is the last stage');
    expect(screen.getByRole('button', {name: 'Done'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Back'})).toBeEnabled();
  });
});
