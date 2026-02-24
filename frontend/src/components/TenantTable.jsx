import React from 'react';
import { toCurrency, toDate } from '../utils/format';

const TenantTable = ({ tenants, onEdit, onDelete }) => {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Room</th>
            <th>Rent</th>
            <th>Due</th>
            <th>Paid Rent</th>
            <th>Paid Deposit</th>
            <th>Joining</th>
            <th>Sharing</th>
            <th>Payment</th>
            <th>Verification</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {tenants.map((tenant) => (
            <tr key={tenant.id}>
              <td data-label="Name">{tenant.fullName}</td>
              <td data-label="Room">{tenant.roomNumber}</td>
              <td data-label="Rent">{toCurrency(tenant.rent)}</td>
              <td data-label="Due">{toCurrency(tenant.rentDueAmount)}</td>
              <td data-label="Paid Rent">{toCurrency(tenant.rentPaidAmount)}</td>
              <td data-label="Paid Deposit">{toCurrency(tenant.depositPaidAmount)}</td>
              <td data-label="Joining">{toDate(tenant.joiningDate)}</td>
              <td data-label="Sharing">{tenant.sharing}</td>
              <td data-label="Payment">{tenant.paymentStatus}</td>
              <td data-label="Verification">{tenant.verificationStatus}</td>
              <td data-label="Actions" className="actions">
                <button onClick={() => onEdit(tenant)}>Edit</button>
                <button className="danger" onClick={() => onDelete(tenant.id)}>Delete</button>
              </td>
            </tr>
          ))}
          {tenants.length === 0 && (
            <tr>
              <td colSpan="11" className="empty-state">No tenant records yet.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
};

export default TenantTable;
