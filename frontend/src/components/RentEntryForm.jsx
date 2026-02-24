import React, { useState } from 'react';

const RentEntryForm = ({ tenants, accounts, onSubmit }) => {
  const [form, setForm] = useState({
    tenantId: '',
    dueDate: '',
    dueAmount: '',
    accountId: ''
  });

  const handleSubmit = (event) => {
    event.preventDefault();
    onSubmit({
      tenantId: Number(form.tenantId),
      billingMonth: form.dueDate,
      dueAmount: Number(form.dueAmount),
      paidAmount: 0,
      accountId: form.accountId ? Number(form.accountId) : null
    });
  };

  return (
    <form className="form" onSubmit={handleSubmit}>
      <h3>Add Tenant Dues (Optional)</h3>
      <div className="form-grid">
        <select required value={form.tenantId} onChange={(e) => setForm((prev) => ({ ...prev, tenantId: e.target.value }))}>
          <option value="">Select Tenant</option>
          {tenants.map((tenant) => (
            <option key={tenant.id} value={tenant.id}>
              {tenant.fullName} - Room {tenant.roomNumber}
            </option>
          ))}
        </select>
        <input type="date" required aria-label="Due Date" title="Due Date" value={form.dueDate} onChange={(e) => setForm((prev) => ({ ...prev, dueDate: e.target.value }))} />
        <input type="number" required placeholder="Total Due Amount" value={form.dueAmount} onChange={(e) => setForm((prev) => ({ ...prev, dueAmount: e.target.value }))} />
        <select value={form.accountId} onChange={(e) => setForm((prev) => ({ ...prev, accountId: e.target.value }))}>
          <option value="">Select Account (Optional)</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name} ({account.mode})
            </option>
          ))}
        </select>
      </div>
      <button type="submit">Add Due Entry</button>
    </form>
  );
};

export default RentEntryForm;
