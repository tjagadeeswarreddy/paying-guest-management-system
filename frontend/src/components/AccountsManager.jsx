import React, { useState } from 'react';

const defaultForm = {
  name: '',
  mode: 'BANK'
};

const AccountsManager = ({ accounts, onSave, onDelete }) => {
  const [form, setForm] = useState(defaultForm);
  const [editingId, setEditingId] = useState(null);

  const submit = (event) => {
    event.preventDefault();
    onSave(
      {
        name: String(form.name || '').trim(),
        mode: String(form.mode || '').trim().toUpperCase()
      },
      editingId
    );
    setForm(defaultForm);
    setEditingId(null);
  };

  return (
    <>
      <form className="form" onSubmit={submit}>
        <h3>{editingId ? 'Update Account' : 'Add Account'}</h3>
        <div className="form-grid">
          <input
            required
            value={form.name}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            placeholder="Account Name (e.g., HDFC, Cash Box)"
          />
          <select
            value={form.mode}
            onChange={(e) => setForm((prev) => ({ ...prev, mode: e.target.value }))}
          >
            <option value="BANK">Bank</option>
            <option value="CASH">Cash</option>
            <option value="UPI">UPI</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div className="form-actions">
          <button type="submit">{editingId ? 'Update Account' : 'Add Account'}</button>
          {editingId && (
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setEditingId(null);
                setForm(defaultForm);
              }}
            >
              Cancel
            </button>
          )}
        </div>
      </form>

      <section className="section-panel">
        <h3>Accounts</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Account Name</th>
                <th>Mode</th>
                <th>Edit</th>
                <th>Delete</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td data-label="Account Name">{account.name}</td>
                  <td data-label="Mode">{account.mode}</td>
                  <td data-label="Edit">
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => {
                        setEditingId(account.id);
                        setForm({ name: account.name || '', mode: account.mode || 'BANK' });
                      }}
                    >
                      Edit
                    </button>
                  </td>
                  <td data-label="Delete">
                    <button type="button" className="danger" onClick={() => onDelete(account.id)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {accounts.length === 0 && (
                <tr>
                  <td colSpan="4" className="empty-state">No accounts found. Add one to start tagging collections.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
};

export default AccountsManager;
