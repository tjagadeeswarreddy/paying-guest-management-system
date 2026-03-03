import React from 'react';
import { toCurrency, toDate } from '../utils/format';

const DeletedTenantsTable = ({ tenants, onRestore, onDeletePermanent }) => {
  return (
    <section className="section-panel">
      <h3>Deleted Tenants</h3>
      <div className="tenant-rows-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Room</th>
              <th>Phone</th>
              <th>Joining</th>
              <th>Checkout</th>
              <th>Rent</th>
              <th>Due</th>
              <th>Restore</th>
              <th>Permanent Delete</th>
            </tr>
          </thead>
          <tbody>
            {tenants.map((tenant) => (
              <tr key={tenant.id}>
                <td data-label="Name">{tenant.fullName}</td>
                <td data-label="Room">{tenant.roomNumber || '-'}</td>
                <td data-label="Phone">{tenant.tenantPhoneNumber || '-'}</td>
                <td data-label="Joining">{toDate(tenant.joiningDate)}</td>
                <td data-label="Checkout">{toDate(tenant.checkoutDate)}</td>
                <td data-label="Rent">{toCurrency(tenant.rent)}</td>
                <td data-label="Due">{toCurrency(tenant.rentDueAmount)}</td>
                <td data-label="Restore">
                  <button className="secondary" onClick={() => onRestore(tenant.id)}>
                    Restore
                  </button>
                </td>
                <td data-label="Permanent Delete">
                  <button className="danger" onClick={() => onDeletePermanent(tenant.id)}>
                    Delete Permanently
                  </button>
                </td>
              </tr>
            ))}
            {tenants.length === 0 && (
              <tr>
                <td colSpan="9" className="empty-state">No deleted tenants found.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
};

export default React.memo(DeletedTenantsTable);
