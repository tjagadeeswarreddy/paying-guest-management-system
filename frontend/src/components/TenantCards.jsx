import React, { useEffect, useMemo, useState } from 'react';
import { toCurrency, toDate } from '../utils/format';

const TENANT_SORT_STORAGE_KEY = 'tenantRowsSortState';
const allowedSortKeys = new Set(['fullName', 'roomNumber', 'joiningDate', 'dueAmount']);
const allowedSortDirections = new Set(['asc', 'desc']);

const getInitialSortState = () => {
  if (typeof window === 'undefined') {
    return { key: null, direction: 'asc' };
  }
  try {
    const raw = window.localStorage.getItem(TENANT_SORT_STORAGE_KEY);
    if (!raw) return { key: null, direction: 'asc' };
    const parsed = JSON.parse(raw);
    const key = allowedSortKeys.has(parsed?.key) ? parsed.key : null;
    const direction = allowedSortDirections.has(parsed?.direction) ? parsed.direction : 'asc';
    return { key, direction };
  } catch (error) {
    return { key: null, direction: 'asc' };
  }
};

const initials = (name = '') =>
  name
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

const todayInputDate = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const TenantCards = ({ tenants, onEdit, onDelete, focusedTenantId, onAddDue, paidByTenantId = {} }) => {
  const [selectedTenantId, setSelectedTenantId] = useState(null);
  const [sortBy, setSortBy] = useState(getInitialSortState);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchType, setSearchType] = useState('name');
  const [openDueTenantId, setOpenDueTenantId] = useState(null);
  const [dueDraftByTenantId, setDueDraftByTenantId] = useState({});
  const [dueDateByTenantId, setDueDateByTenantId] = useState({});
  const [submittingDueTenantId, setSubmittingDueTenantId] = useState(null);

  useEffect(() => {
    if (!focusedTenantId) return;
    const exists = tenants.some((tenant) => tenant.id === focusedTenantId);
    if (exists) {
      setSelectedTenantId(focusedTenantId);
    }
  }, [focusedTenantId, tenants]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(TENANT_SORT_STORAGE_KEY, JSON.stringify(sortBy));
  }, [sortBy]);

  const filteredTenants = useMemo(() => {
    if (!searchQuery.trim()) return tenants;
    
    const query = searchQuery.trim().toLowerCase();
    
    return tenants.filter(tenant => {
      // Search by name (full name)
      if (searchType === 'name') {
        return tenant.fullName && tenant.fullName.toLowerCase().includes(query);
      }
      
      // Search by phone number
      if (searchType === 'phone') {
        return tenant.tenantPhoneNumber && tenant.tenantPhoneNumber.includes(query);
      }
      
      // Search by room number
      if (searchType === 'room') {
        return tenant.roomNumber && tenant.roomNumber.toLowerCase().includes(query);
      }
      
      // Default to search all fields if no specific type selected
      const nameMatch = tenant.fullName && tenant.fullName.toLowerCase().includes(query);
      const phoneMatch = tenant.tenantPhoneNumber && tenant.tenantPhoneNumber.includes(query);
      const roomMatch = tenant.roomNumber && tenant.roomNumber.toLowerCase().includes(query);
      
      return nameMatch || phoneMatch || roomMatch;
    });
  }, [tenants, searchQuery, searchType]);

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
    const list = [...filteredTenants];
    if (!sortBy.key) {
      const activeRows = list.filter((tenant) => tenant.active);
      const deletedRows = list.filter((tenant) => !tenant.active);
      return [...activeRows, ...deletedRows];
    }

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
    const activeRows = list.filter((tenant) => tenant.active);
    const deletedRows = list.filter((tenant) => !tenant.active);
    return [...activeRows, ...deletedRows];
  }, [filteredTenants, sortBy]);

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

  const handleAddDue = async (tenant) => {
    if (!onAddDue) return;
    const amount = Math.max(Number(dueDraftByTenantId[tenant.id] || 0), 0);
    if (amount <= 0) return;
    try {
      setSubmittingDueTenantId(tenant.id);
      await onAddDue(tenant, amount, dueDateByTenantId[tenant.id] || todayInputDate());
      setDueDraftByTenantId((prev) => ({ ...prev, [tenant.id]: '' }));
      setDueDateByTenantId((prev) => ({ ...prev, [tenant.id]: todayInputDate() }));
      setOpenDueTenantId(null);
    } finally {
      setSubmittingDueTenantId(null);
    }
  };

  return (
    <section className="section-panel">
      <div className="filter-panel">
        <div className="filter-header-row">
          <h3>Tenant Rows</h3>
          <div className="search-container">
            <div className="search-controls">
              <select
                value={searchType}
                onChange={(e) => setSearchType(e.target.value)}
                className="search-type-select"
              >
                <option value="name">Search by Name</option>
                <option value="phone">Search by Phone</option>
                <option value="room">Search by Room</option>
              </select>
              <div className="search-input-wrapper">
                <input
                  type="text"
                  placeholder={`Search ${searchType === 'name' ? 'by name' : searchType === 'phone' ? 'by phone' : 'by room'}...`}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="search-input"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
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
              <th>Add Due</th>
              <th>View</th>
              <th>Edit</th>
              <th>Delete</th>
            </tr>
          </thead>
          <tbody>
            {sortedTenants.map((tenant) => {
              const totalPaid = Number(paidByTenantId[tenant.id] || 0);
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
                  <td data-label="Add Due">
                    {tenant.active ? (
                      <div style={{ position: 'relative' }}>
                        <button
                          className="secondary"
                          onClick={() => setOpenDueTenantId((prev) => (prev === tenant.id ? null : tenant.id))}
                          disabled={submittingDueTenantId === tenant.id}
                        >
                          Add Due ▾
                        </button>
                        {openDueTenantId === tenant.id && (
                          <div className="section-panel" style={{ position: 'absolute', zIndex: 2, right: 0, minWidth: '11rem', padding: '0.55rem' }}>
                            <input
                              type="number"
                              min="0"
                              placeholder="Due amount"
                              value={dueDraftByTenantId[tenant.id] ?? ''}
                              onChange={(e) => setDueDraftByTenantId((prev) => ({ ...prev, [tenant.id]: e.target.value }))}
                            />
                            <input
                              type="date"
                              value={dueDateByTenantId[tenant.id] || todayInputDate()}
                              onChange={(e) => setDueDateByTenantId((prev) => ({ ...prev, [tenant.id]: e.target.value }))}
                              style={{ marginTop: '0.45rem' }}
                            />
                            <div className="actions" style={{ marginTop: '0.45rem' }}>
                              <button
                                className="secondary"
                                onClick={() => handleAddDue(tenant)}
                                disabled={submittingDueTenantId === tenant.id}
                              >
                                {submittingDueTenantId === tenant.id ? 'Adding...' : 'Add'}
                              </button>
                              <button
                                className="secondary"
                                onClick={() => setOpenDueTenantId(null)}
                                disabled={submittingDueTenantId === tenant.id}
                              >
                                Cancel
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    ) : (
                      <span>-</span>
                    )}
                  </td>
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
                    {tenant.active ? (
                      <button className="danger" onClick={() => onDelete(tenant.id)}>Delete</button>
                    ) : (
                      <span>Deleted</span>
                    )}
                  </td>
                </tr>
              );
            })}
            {sortedTenants.length === 0 && (
              <tr>
                <td colSpan="12" className="empty-state">No tenants found.</td>
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

export default React.memo(TenantCards);
