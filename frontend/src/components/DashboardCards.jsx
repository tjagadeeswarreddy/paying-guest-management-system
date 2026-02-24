import React from 'react';
import { toCurrency } from '../utils/format';

const DashboardCards = ({ summary }) => {
  const cards = [
    { label: 'Total Collection', value: toCurrency(summary.totalRentCollection) },
    { label: 'Daily Collection', value: toCurrency(summary.totalDailyCollection) },
    { label: 'Total Due', value: toCurrency(summary.totalDueAmount) },
    { label: 'Pending Collection', value: toCurrency(summary.totalPendingCollection) },
    { label: 'Active Tenants', value: summary.activeTenants || 0 },
    { label: 'Daily Tenants', value: summary.dailyTenants || 0 },
    { label: 'Paid Tenants (Month)', value: summary.paidTenantsThisMonth || 0 },
    { label: 'Tenants With Due', value: summary.dueTenantsThisMonth || 0 },
    { label: 'Partial Payments', value: summary.partialTenantsThisMonth || 0 },
    { label: 'Total Beds', value: summary.totalBeds || 0 },
    { label: 'Occupied Beds', value: summary.occupiedBeds || 0 },
    { label: 'Vacant Beds', value: summary.vacantBeds || 0 }
  ];

  return (
    <div className="cards-grid">
      {cards.map((card) => (
        <article key={card.label} className="metric-card">
          <p>{card.label}</p>
          <h3>{card.value}</h3>
        </article>
      ))}
    </div>
  );
};

export default DashboardCards;
