import React, { useMemo, useState } from 'react';
import { toCurrency, toDate } from '../utils/format';

const PRESET_TAGS = [
  'Groceries',
  'Electricity',
  'Water Bill',
  'Maintenance',
  'Internet',
  'Salary',
  'Other'
];

const initialForm = {
  description: '',
  amount: '',
  transactionDate: '',
  tag: 'Groceries',
  customTag: '',
  accountId: ''
};

const ExpensesManager = ({ expenses, accounts, onSave, onDelete }) => {
  const [form, setForm] = useState(initialForm);
  const [editingId, setEditingId] = useState(null);
  const [accountFilter, setAccountFilter] = useState('ALL');
  const [tagFilter, setTagFilter] = useState('ALL');

  const availableTags = useMemo(
    () => Array.from(new Set((expenses || []).map((expense) => expense.tag).filter(Boolean))),
    [expenses]
  );
  const filteredExpenses = useMemo(
    () =>
      (expenses || []).filter((expense) => {
        const accountMatches = accountFilter === 'ALL' || String(expense.accountId || '') === String(accountFilter);
        const tagMatches = tagFilter === 'ALL' || String(expense.tag || '') === String(tagFilter);
        return accountMatches && tagMatches;
      }),
    [accountFilter, expenses, tagFilter]
  );
  const totalExpenses = useMemo(
    () => filteredExpenses.reduce((sum, expense) => sum + Number(expense.amount || 0), 0),
    [filteredExpenses]
  );

  const submit = (event) => {
    event.preventDefault();
    onSave(
      {
        description: String(form.description || '').trim(),
        amount: Number(form.amount || 0),
        transactionDate: form.transactionDate,
        tag: form.tag === 'Other' ? String(form.customTag || '').trim() : form.tag,
        accountId: form.accountId ? Number(form.accountId) : null
      },
      editingId
    );
    setEditingId(null);
    setForm(initialForm);
  };

  return (
    <>
      <form className="form" onSubmit={submit}>
        <h3>{editingId ? 'Update Expense' : 'Add Expense'}</h3>
        <div className="form-grid">
          <input
            required
            value={form.description}
            onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            placeholder="Expense description"
          />
          <input
            required
            type="number"
            min="0.01"
            step="0.01"
            value={form.amount}
            onChange={(e) => setForm((prev) => ({ ...prev, amount: e.target.value }))}
            placeholder="Amount"
          />
          <input
            required
            type="date"
            value={form.transactionDate}
            onChange={(e) => setForm((prev) => ({ ...prev, transactionDate: e.target.value }))}
          />
          <select
            value={form.tag}
            onChange={(e) => setForm((prev) => ({ ...prev, tag: e.target.value }))}
          >
            {PRESET_TAGS.map((tag) => (
              <option key={tag} value={tag}>{tag}</option>
            ))}
          </select>
          {form.tag === 'Other' && (
            <input
              required
              value={form.customTag}
              onChange={(e) => setForm((prev) => ({ ...prev, customTag: e.target.value }))}
              placeholder="Custom tag"
            />
          )}
          <select
            value={form.accountId}
            onChange={(e) => setForm((prev) => ({ ...prev, accountId: e.target.value }))}
          >
            <option value="">Select Account (Optional)</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name} ({account.mode})
              </option>
            ))}
          </select>
        </div>
        <div className="form-actions">
          <button type="submit">{editingId ? 'Update Expense' : 'Add Expense'}</button>
          {editingId && (
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setEditingId(null);
                setForm(initialForm);
              }}
            >
              Cancel
            </button>
          )}
        </div>
      </form>

      <section className="section-panel filter-panel">
        <div className="filter-header-row">
          <h3>Expense Filters</h3>
        </div>
        <div className="filter-bar standard-filter">
          <select value={accountFilter} onChange={(e) => setAccountFilter(e.target.value)}>
            <option value="ALL">All Accounts</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name} ({account.mode})
              </option>
            ))}
          </select>
          <select value={tagFilter} onChange={(e) => setTagFilter(e.target.value)}>
            <option value="ALL">All Tags</option>
            {availableTags.map((tag) => (
              <option key={tag} value={tag}>{tag}</option>
            ))}
          </select>
        </div>
      </section>

      <section className="cards-grid due-summary-grid">
        <article className="metric-card">
          <p>Total Expenses</p>
          <h3>{toCurrency(totalExpenses)}</h3>
        </article>
      </section>

      <section className="section-panel">
        <h3>Expenses ({filteredExpenses.length})</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Description</th>
                <th>Amount</th>
                <th>Date</th>
                <th>Tag</th>
                <th>Account</th>
                <th>Edit</th>
                <th>Delete</th>
              </tr>
            </thead>
            <tbody>
              {filteredExpenses.map((expense) => (
                <tr key={expense.id}>
                  <td data-label="Description">{expense.description}</td>
                  <td data-label="Amount">{toCurrency(expense.amount)}</td>
                  <td data-label="Date">{toDate(expense.transactionDate)}</td>
                  <td data-label="Tag">{expense.tag || '-'}</td>
                  <td data-label="Account">{expense.accountName || '-'}</td>
                  <td data-label="Edit">
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => {
                        setEditingId(expense.id);
                        const existingTag = expense.tag || '';
                        const isPresetTag = PRESET_TAGS.includes(existingTag);
                        setForm({
                          description: expense.description || '',
                          amount: String(expense.amount ?? ''),
                          transactionDate: expense.transactionDate || '',
                          tag: isPresetTag ? existingTag : 'Other',
                          customTag: isPresetTag ? '' : existingTag,
                          accountId: expense.accountId ? String(expense.accountId) : ''
                        });
                      }}
                    >
                      Edit
                    </button>
                  </td>
                  <td data-label="Delete">
                    <button type="button" className="danger" onClick={() => onDelete(expense.id)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {filteredExpenses.length === 0 && (
                <tr>
                  <td colSpan="7" className="empty-state">No expenses added.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
};

export default React.memo(ExpensesManager);
