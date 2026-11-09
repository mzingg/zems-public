'use client';

import {useEffect} from 'react';
import {createRoot} from 'react-dom/client';
import {Stepper} from '@/app/stepper';
import type {Journey} from '@/docs-source/blocks';

/** Replaces every server-rendered journey ladder by the interactive stepper; the ladder stays for readers without JS. */
export function JourneySteppers() {
  useEffect(() => {
    for (const ladder of document.querySelectorAll<HTMLOListElement>('ol.journey[data-journey]')) {
      const journey = JSON.parse(ladder.dataset.journey!) as Journey;
      const mount = document.createElement('div');
      ladder.replaceWith(mount);
      createRoot(mount).render(<Stepper journey={journey} />);
    }
  }, []);
  return null;
}
