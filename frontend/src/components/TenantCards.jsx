import React, { useEffect, useMemo, useState } from 'react';
import { toCurrency, toDate } from '../utils/format';

const initials = (name = '') =>
  name
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

const TenantCards = ({ tenants, onEdit, onDelete, focusedTenantId }) => {
  const [selectedTenantId, setSelectedTenantId] = useState(null);
  const [sortBy, setSortBy] = useState({ key: null, direction: 'asc' });

  useEffect(() => {
    if (!focusedTenantId) return;
    const exists = tenants.some((tenant) => tenant.id === focusedTenantId);
    if (exists) {
      setSelectedTenantId(focusedTenantId);
    }
  }, [focusedTenantId, tenants]);

  const selectedTenant = useMemo(
    () => tenants.find((tenant) => tenant.id === selectedTenantId) || null,
    [tenants, selectedTenantId]
  );

  const roomSortValue = (roomNumber = '') => {
    if (roomNumber.startsWith('G')) {
      const groundIndex = Number(roomNumber.replace('G', '')) || 0;
      return groundIndex;
    }
    const numeric = Number(roomNumber);
    return Number.isNaN(numeric) ? Number.MAX_SAFE_INTEGER : 1000 + numeric;
  };

  const dueForTenant = (tenant) =>
    Math.max(
      Number(tenant.rentDueAmount || 0) + Math.max(Number(tenant.deposit || 0) - Number(tenant.depositPaidAmount || 0), 0),
      0
    );

  const sortedTenants = useMemo(() => {
    const list = [...tenants];
    if (!sortBy.key) return list;

    list.sort((a, b) => {
      let compare = 0;
      if (sortBy.key === 'fullName') {
        compare = String(a.fullName || '').localeCompare(String(b.fullName || ''));
      } else if (sortBy.key === 'roomNumber') {
        compare = roomSortValue(a.roomNumber) - roomSortValue(b.roomNumber);
      } else if (sortBy.key === 'joiningDate') {
        compare = String(a.joiningDate || '').localeCompare(String(b.joiningDate || ''));
      } else if (sortBy.key === 'dueAmount') {
        compare = dueForTenant(a) - dueForTenant(b);
      }
      return sortBy.direction === 'asc' ? compare : -compare;
    });
    return list;
  }, [tenants, sortBy]);

  const toggleSort = (key) => {
    setSortBy((prev) => {
      if (prev.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      return { key, direction: 'asc' };
    });
  };

  const sortMarker = (key) => {
    if (sortBy.key !== key) return '';
    return sortBy.direction === 'asc' ? ' ▲' : ' ▼';
  };

  return (
    <section className="section-panel">
      <h3>Tenant Rows</h3>
      <div className="tenant-rows-wrap">
        <table>
          <thead>
            <tr>
              <th onClick={() => toggleSort('fullName')} style={{ cursor: 'pointer' }}>
                Name{sortMarker('fullName')}
              </th>
              <th onClick={() => toggleSort('roomNumber')} style={{ cursor: 'pointer' }}>
                Room{sortMarker('roomNumber')}
              </th>
              <th>Phone</th>
              <th onClick={() => toggleSort('joiningDate')} style={{ cursor: 'pointer' }}>
                Joining Date{sortMarker('joiningDate')}
              </th>
              <th>Rent</th>
              <th>Paid</th>
              <th onClick={() => toggleSort('dueAmount')} style={{ cursor: 'pointer' }}>
                Due{sortMarker('dueAmount')}
              </th>
              <th>Status</th>
              <th>View</th>
              <th>Edit</th>
              <th>Delete</th>
            </tr>
          </thead>
          <tbody>
            {sortedTenants.map((tenant) => {
              const totalPaid = Number(tenant.rentPaidAmount || 0) + Number(tenant.depositPaidAmount || 0);
              const totalDue = dueForTenant(tenant);
              const rowClass = totalDue <= 0 ? 'tenant-row-paid' : 'tenant-row-due';
              return (
              <tr key={tenant.id} className={rowClass}>
                <td data-label="Name">{tenant.fullName}</td>
                <td data-label="Room">{tenant.roomNumber}</td>
                <td data-label="Phone">{tenant.tenantPhoneNumber || '-'}</td>
                <td data-label="Joining Date">{toDate(tenant.joiningDate)}</td>
                <td data-label="Rent">{toCurrency(tenant.rent)}</td>
                <td data-label="Paid">{toCurrency(totalPaid)}</td>
                <td data-label="Due">{toCurrency(totalDue)}</td>
                <td data-label="Status">{tenant.paymentStatus}</td>
                <td data-label="View">
                  <button
                    className="secondary"
                    onClick={() => setSelectedTenantId((prev) => (prev === tenant.id ? null : tenant.id))}
                  >
                    {selectedTenantId === tenant.id ? 'Hide' : 'View'}
                  </button>
                </td>
                <td data-label="Edit">
                  <button className="secondary" onClick={() => onEdit(tenant)}>Edit</button>
                </td>
                <td data-label="Delete">
                  <button className="danger" onClick={() => onDelete(tenant.id)}>Delete</button>
                </td>
              </tr>
              );
            })}
            {tenants.length === 0 && (
              <tr>
                <td colSpan="11" className="empty-state">No tenants found.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {selectedTenant && (
        <div
          className="tenant-detail-modal-backdrop"
          onClick={() => setSelectedTenantId(null)}
          role="presentation"
        >
          <section
            className="tenant-detail-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="tenant-detail-head">
              <div className="avatar-wrap">
                <span>{initials(selectedTenant.fullName)}</span>
              </div>
              <div>
                <h4>{selectedTenant.fullName}</h4>
                <p>
                  Room {selectedTenant.roomNumber} • Joined {toDate(selectedTenant.joiningDate)}
                </p>
              </div>
              <button
                type="button"
                className="tenant-detail-close"
                onClick={() => setSelectedTenantId(null)}
                aria-label="Close tenant details"
                title="Close"
              >
                ×
              </button>
            </div>
            <div className="tenant-detail-grid">
              <div>
                <small>Rent</small>
                <strong>{toCurrency(selectedTenant.rent)}</strong>
              </div>
              <div>
                <small>Rent Paid</small>
                <strong>{toCurrency(selectedTenant.rentPaidAmount)}</strong>
              </div>
              <div>
                <small>Rent Due</small>
                <strong>{toCurrency(selectedTenant.rentDueAmount)}</strong>
              </div>
              <div>
                <small>Deposit</small>
                <strong>{toCurrency(selectedTenant.deposit)}</strong>
              </div>
              <div>
                <small>Deposit Paid</small>
                <strong>{toCurrency(selectedTenant.depositPaidAmount)}</strong>
              </div>
              <div>
                <small>Tenant Phone</small>
                <strong>{selectedTenant.tenantPhoneNumber || '-'}</strong>
              </div>
              <div>
                <small>Emergency Contact</small>
                <strong>{selectedTenant.emergencyContactNumber || '-'}</strong>
              </div>
              <div>
                <small>Emergency Contact Name</small>
                <strong>{selectedTenant.emergencyContactRelationship || '-'}</strong>
              </div>
              <div>
                <small>Company</small>
                <strong>{selectedTenant.companyName || '-'}</strong>
              </div>
              <div>
                <small>Company Address</small>
                <strong>{selectedTenant.companyAddress || '-'}</strong>
              </div>
              <div>
                <small>Verification</small>
                <strong>{selectedTenant.verificationStatus}</strong>
              </div>
              <div>
                <small>Payment Status</small>
                <strong>{selectedTenant.paymentStatus}</strong>
              </div>
            </div>
          </section>
        </div>
      )}
    </section>
  );
};

export default TenantCards;
