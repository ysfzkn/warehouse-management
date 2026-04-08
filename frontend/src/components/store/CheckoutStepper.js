import React from 'react';

const STEPS = [
  { label: 'Adres', key: 'address' },
  { label: 'Kargo', key: 'shipping' },
  { label: 'Ödeme', key: 'payment' },
  { label: 'Onay', key: 'confirm' },
];

export default function CheckoutStepper({ currentStep }) {
  const currentIndex = STEPS.findIndex(s => s.key === currentStep);
  return (
    <nav aria-label="Checkout adımları">
      <ol className="checkout-stepper" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {STEPS.map((step, i) => (
          <li key={step.key} className="d-flex align-items-center">
            {i > 0 && <div className={`checkout-step-line ${i <= currentIndex ? 'completed' : ''}`} />}
            <div
              className={`checkout-step ${i === currentIndex ? 'active' : ''} ${i < currentIndex ? 'completed' : ''}`}
              aria-current={i === currentIndex ? 'step' : undefined}
            >
              <div className="checkout-step-number" aria-hidden="true">
                {i < currentIndex ? '\u2713' : i + 1}
              </div>
              <span className="checkout-step-label">{step.label}</span>
            </div>
          </li>
        ))}
      </ol>
    </nav>
  );
}
