import React from 'react';
import DynamicSagasuBanner from '../components/DynamicSagasuBanner';
import MapSection from '../components/MapSection';
import ProcessSection from '../components/ProcessSection';
import DualCtaSection from '../components/DualCtaSection';

export default function HomePage() {
  return (
    <div className="flex-1 flex flex-col">
      <DynamicSagasuBanner />
      <MapSection />
      <ProcessSection />
      <DualCtaSection />
    </div>
  );
}

