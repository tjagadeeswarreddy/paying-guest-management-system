import React, { useState } from 'react';
import { toCurrency, toDate } from '../utils/format';

const DailyTenantTable = ({ tenants, onEdit, onDelete, onCheckout }) => {
  const [selectedTenant, setSelectedTenant] = useState(null);
  const totalDailyCollection = tenants.reduce(
    (sum, tenant) => sum + Number(tenant.dailyCollectionAmount || 0),
    0
  );
  const totalOutstandingDue = tenants.reduce((sum, tenant) => {
    const payable = Number(tenant.rent || 0);
    const collected = Number(tenant.dailyCollectionAmount || 0);
    return sum + Math.max(payable - collected, 0);
  }, 0);

  return (
    <>
      <section className="cards-grid due-summary-grid">
        <article className="metric-card">
          <p>Daily Tenants</p>
          <h3>{tenants.length}</h3>
        </article>
        <article className="metric-card">
          <p>Daily Collection Total</p>
          <h3>{toCurrency(totalDailyCollection)}</h3>
        </article>
        <article className="metric-card">
          <p>Total Outstanding Due</p>
          <h3>{toCurrency(totalOutstandingDue)}</h3>
        </article>
      </section>

      <section className="section-panel">
        <h3>Daily Accommodation Tenants</h3>
        <div className="tenant-rows-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Phone</th>
                <th>Room</th>
                <th>Food</th>
                <th>Stay Days</th>
                <th>Daily Rent</th>
                <th>Collected</th>
                <th>Joining Date</th>
                <th>View</th>
                <th>Edit</th>
                <th>Checkout</th>
                <th>Delete</th>
              </tr>
            </thead>
            <tbody>
              {tenants.map((tenant) => (
                <tr key={tenant.id}>
                  <td data-label="Name">{tenant.fullName}</td>
                  <td data-label="Phone">{tenant.tenantPhoneNumber || '-'}</td>
                  <td data-label="Room">{tenant.roomNumber}</td>
                  <td data-label="Food">{tenant.dailyFoodOption || '-'}</td>
                  <td data-label="Stay Days">{tenant.dailyStayDays || 1}</td>
                  <td data-label="Daily Rent">{toCurrency(tenant.rent)}</td>
                  <td data-label="Collected">{toCurrency(tenant.dailyCollectionAmount || 0)}</td>
                  <td data-label="Joining Date">{toDate(tenant.joiningDate)}</td>
                  <td data-label="View">
                    <button className="secondary" onClick={() => setSelectedTenant(tenant)}>View</button>
                  </td>
                  <td data-label="Edit">
                    <button className="secondary" onClick={() => onEdit(tenant)}>Edit</button>
                  </td>
                  <td data-label="Checkout">
                    <button className="secondary" onClick={() => onCheckout(tenant.id)}>Checkout</button>
                  </td>
                  <td data-label="Delete">
                    <button className="danger" onClick={() => onDelete(tenant.id)}>Delete</button>
                  </td>
                </tr>
              ))}
              {tenants.length === 0 && (
                <tr>
                  <td colSpan="12" className="empty-state">No daily accommodation tenants found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {selectedTenant && (
        <div
          className="tenant-detail-modal-backdrop"
          onClick={() => setSelectedTenant(null)}
          role="presentation"
        >
          <section
            className="tenant-detail-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="tenant-detail-head">
              <div>
                <h4>{selectedTenant.fullName}</h4>
                <p>Daily Tenant • Room {selectedTenant.roomNumber}</p>
              </div>
              <button
                type="button"
                className="tenant-detail-close"
                onClick={() => setSelectedTenant(null)}
                aria-label="Close daily tenant details"
                title="Close"
              >
                ×
              </button>
            </div>
            <div className="tenant-detail-grid">
              <div><small>Phone</small><strong>{selectedTenant.tenantPhoneNumber || '-'}</strong></div>
              <div><small>Food</small><strong>{selectedTenant.dailyFoodOption || '-'}</strong></div>
              <div><small>Stay Days</small><strong>{selectedTenant.dailyStayDays || 1}</strong></div>
              <div><small>Daily Rent</small><strong>{toCurrency(selectedTenant.rent)}</strong></div>
              <div><small>Daily Collection</small><strong>{toCurrency(selectedTenant.dailyCollectionAmount || 0)}</strong></div>
              <div><small>Joining Date</small><strong>{toDate(selectedTenant.joiningDate)}</strong></div>
            </div>
          </section>
        </div>
      )}
    </>
  );
};

export default DailyTenantTable;
