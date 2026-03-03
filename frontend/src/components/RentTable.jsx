import React, { useMemo, useState } from 'react';
import { toCurrency, toDate, toDateTime } from '../utils/format';

const RentTable = ({
  title,
  records,
  showPayAction,
  onPay,
  onDelete,
  onUpdate,
  showTransaction,
  dateLabel = 'Month',
  enableSort = false,
  accounts = [],
  onRowClick = null,
  onRemind = null,
  onReceipt = null,
  onPreviewReceipt = null,
  receiptBusyKey = null
}) => {
  const [editingId, setEditingId] = useState(null);
  const [editingRecord, setEditingRecord] = useState(null);
  const [editForm, setEditForm] = useState({ dueAmount: '', paidAmount: '', accountId: '' });
  const [editBaseline, setEditBaseline] = useState({ dueAmount: 0, paidAmount: 0 });
  const [dueManuallyEdited, setDueManuallyEdited] = useState(false);
  const [payingId, setPayingId] = useState(null);
  const [payMode, setPayMode] = useState('FULL');
  const [payAmount, setPayAmount] = useState('');
  const [payTransactionDate, setPayTransactionDate] = useState('');
  const [payAccountId, setPayAccountId] = useState('');
  const [sortBy, setSortBy] = useState({ key: null, direction: 'asc' });

  const outstandingDue = (record) =>
    Math.max(Number(record.dueAmount || 0) - Number(record.paidAmount || 0), 0);

  const sortedRecords = useMemo(() => {
    if (!enableSort || !sortBy.key) return records;
    const list = [...records];
    list.sort((a, b) => {
      let compare = 0;
      if (sortBy.key === 'billingMonth') {
        compare = String(a.billingMonth || '').localeCompare(String(b.billingMonth || ''));
      } else if (sortBy.key === 'tenantName') {
        compare = String(a.tenantName || '').localeCompare(String(b.tenantName || ''));
      } else if (sortBy.key === 'roomNumber') {
        compare = String(a.roomNumber || '').localeCompare(String(b.roomNumber || ''));
      } else if (sortBy.key === 'due') {
        compare = outstandingDue(a) - outstandingDue(b);
      } else if (sortBy.key === 'paidAmount') {
        compare = Number(a.paidAmount || 0) - Number(b.paidAmount || 0);
      } else if (sortBy.key === 'status') {
        compare = String(a.status || '').localeCompare(String(b.status || ''));
      }
      return sortBy.direction === 'asc' ? compare : -compare;
    });
    return list;
  }, [enableSort, records, sortBy]);

  const toggleSort = (key) => {
    if (!enableSort) return;
    setSortBy((prev) => {
      if (prev.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      return { key, direction: 'asc' };
    });
  };

  const sortMarker = (key) => {
    if (!enableSort || sortBy.key !== key) return '';
    return sortBy.direction === 'asc' ? ' ▲' : ' ▼';
  };

  const startEdit = (record) => {
    const key = `${record.collectionType || 'RENT_RECORD'}-${record.id}`;
    const baseDue = Number(outstandingDue(record) || 0);
    const basePaid = Number(record.paidAmount || 0);
    setEditingId(key);
    setEditingRecord(record);
    setEditBaseline({ dueAmount: baseDue, paidAmount: basePaid });
    setDueManuallyEdited(false);
    setEditForm({
      dueAmount: String(baseDue),
      paidAmount: String(basePaid),
      accountId: record.accountId ? String(record.accountId) : ''
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditingRecord(null);
    setEditBaseline({ dueAmount: 0, paidAmount: 0 });
    setDueManuallyEdited(false);
    setEditForm({ dueAmount: '', paidAmount: '', accountId: '' });
  };

  const startPay = (record) => {
    const key = `${record.collectionType || 'RENT_RECORD'}-${record.id}`;
    setPayingId(key);
    setPayMode('FULL');
    setPayAmount(String(outstandingDue(record)));
    setPayTransactionDate('');
    setPayAccountId(record.accountId ? String(record.accountId) : '');
  };

  const cancelPay = () => {
    setPayingId(null);
    setPayMode('FULL');
    setPayAmount('');
    setPayTransactionDate('');
    setPayAccountId('');
  };

  const savePay = async (record) => {
    if (!onPay) return;
    await onPay(record, {
      mode: payMode,
      amount: Number(payAmount || 0),
      transactionDate: payTransactionDate || null,
      accountId: payAccountId ? Number(payAccountId) : null
    });
    cancelPay();
  };

  const saveEdit = async () => {
    if (!editingRecord || !onUpdate) return;
    await onUpdate(
      editingRecord,
      {
        dueAmount: Number(editForm.dueAmount || 0),
        paidAmount: Number(editForm.paidAmount || 0),
        accountId: editForm.accountId ? Number(editForm.accountId) : null
      }
    );
    cancelEdit();
  };

  return (
    <section className="section-panel">
      <h3>{title}</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th onClick={() => toggleSort('billingMonth')} style={{ cursor: enableSort ? 'pointer' : 'default' }}>
                {dateLabel}{sortMarker('billingMonth')}
              </th>
              <th onClick={() => toggleSort('tenantName')} style={{ cursor: enableSort ? 'pointer' : 'default' }}>
                Name{sortMarker('tenantName')}
              </th>
              <th onClick={() => toggleSort('roomNumber')} style={{ cursor: enableSort ? 'pointer' : 'default' }}>
                Room{sortMarker('roomNumber')}
              </th>
              <th onClick={() => toggleSort('due')} style={{ cursor: enableSort ? 'pointer' : 'default' }}>
                Due{sortMarker('due')}
              </th>
              <th onClick={() => toggleSort('paidAmount')} style={{ cursor: enableSort ? 'pointer' : 'default' }}>
                Paid{sortMarker('paidAmount')}
              </th>
              <th onClick={() => toggleSort('status')} style={{ cursor: enableSort ? 'pointer' : 'default' }}>
                Status{sortMarker('status')}
              </th>
              <th>Account</th>
              {showTransaction && <th>Transaction Date-Time</th>}
              {onUpdate && <th>Edit Rent</th>}
              {onDelete && <th>Delete Rent</th>}
              {showPayAction && <th>Action</th>}
              {onRemind && <th>Remind</th>}
              {(onReceipt || onPreviewReceipt) && <th>Receipt</th>}
            </tr>
          </thead>
          <tbody>
            {sortedRecords.map((record) => {
              const rowKey = `${record.collectionType || 'RENT_RECORD'}-${record.id}`;
              const isEditing = editingId === rowKey;
              const isPaying = payingId === rowKey;
              const isReceiptBusy = receiptBusyKey === rowKey;
              const dueBalance = outstandingDue(record);
              return (
              <tr
                key={rowKey}
                onClick={() => onRowClick && onRowClick(record)}
                style={onRowClick ? { cursor: 'pointer' } : undefined}
              >
                <td data-label={dateLabel}>{toDate(record.billingMonth)}</td>
                <td data-label="Name">{record.tenantName}</td>
                <td data-label="Room">{record.roomNumber}</td>
                <td data-label="Due">
                  {isEditing ? (
                    <input
                      type="number"
                      min="0"
                      value={editForm.dueAmount}
                      onChange={(e) => {
                        setDueManuallyEdited(true);
                        setEditForm((prev) => ({ ...prev, dueAmount: e.target.value }));
                      }}
                    />
                  ) : (
                    toCurrency(outstandingDue(record))
                  )}
                </td>
                <td data-label="Paid">
                  {isEditing ? (
                    <input
                      type="number"
                      min="0"
                      value={editForm.paidAmount}
                      onChange={(e) => {
                        const nextPaid = Number(e.target.value || 0);
                        setEditForm((prev) => {
                          if (dueManuallyEdited) {
                            return { ...prev, paidAmount: e.target.value };
                          }
                          const paidDelta = nextPaid - Number(editBaseline.paidAmount || 0);
                          const autoDue = Math.max(Number(editBaseline.dueAmount || 0) - paidDelta, 0);
                          return {
                            ...prev,
                            paidAmount: e.target.value,
                            dueAmount: String(autoDue)
                          };
                        });
                      }}
                    />
                  ) : (
                    toCurrency(record.paidAmount)
                  )}
                </td>
                <td data-label="Status">{record.status}</td>
                <td data-label="Account">
                  {isEditing ? (
                    <select
                      value={editForm.accountId}
                      onChange={(e) => setEditForm((prev) => ({ ...prev, accountId: e.target.value }))}
                    >
                      <option value="">No Account</option>
                      {accounts.map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.name}
                        </option>
                      ))}
                    </select>
                  ) : (
                    record.accountName || '-'
                  )}
                </td>
                {showTransaction && <td data-label="Transaction Date-Time">{toDateTime(record.transactionAt)}</td>}
                {onUpdate && (
                  <td data-label="Edit Rent">
                    {isEditing ? (
                      <div className="actions">
                        <button className="secondary" onClick={saveEdit}>Save</button>
                        <button className="secondary" onClick={cancelEdit}>Cancel</button>
                      </div>
                    ) : (
                      <button className="secondary" onClick={() => startEdit(record)}>Edit</button>
                    )}
                  </td>
                )}
                {onDelete && (
                  <td data-label="Delete Rent">
                    <button className="danger" onClick={() => onDelete(record)}>Delete</button>
                  </td>
                )}
                {showPayAction && (
                  <td data-label="Action">
                    {isPaying ? (
                      <div className="actions">
                        <select value={payMode} onChange={(e) => setPayMode(e.target.value)}>
                          <option value="FULL">Fully Paid</option>
                          <option value="PARTIAL">Partial</option>
                        </select>
                        {payMode === 'PARTIAL' && (
                          <input
                            type="number"
                            min="0"
                            max={dueBalance}
                            placeholder="Paid Amount"
                            value={payAmount}
                            onChange={(e) => setPayAmount(e.target.value)}
                          />
                        )}
                        <input
                          type="date"
                          value={payTransactionDate}
                          onChange={(e) => setPayTransactionDate(e.target.value)}
                          placeholder="Transaction Date (Optional)"
                          title="Transaction Date (Optional)"
                        />
                        <select value={payAccountId} onChange={(e) => setPayAccountId(e.target.value)}>
                          <option value="">Select Account</option>
                          {accounts.map((account) => (
                            <option key={account.id} value={account.id}>
                              {account.name}
                            </option>
                          ))}
                        </select>
                        <button className="secondary" onClick={() => savePay(record)}>Save</button>
                        <button className="secondary" onClick={cancelPay}>Cancel</button>
                      </div>
                    ) : (
                      <button disabled={isEditing} onClick={() => startPay(record)}>Mark Paid</button>
                    )}
                  </td>
                )}
                {onRemind && (
                  <td data-label="Remind">
                    <button
                      className="secondary"
                      onClick={() => onRemind(record)}
                    >
                      Remind
                    </button>
                  </td>
                )}
                {(onReceipt || onPreviewReceipt) && (
                  <td data-label="Receipt">
                    <div className="actions">
                      {onPreviewReceipt && (
                        <button
                          className="secondary"
                          disabled={isReceiptBusy}
                          onClick={(e) => {
                            e.stopPropagation();
                            onPreviewReceipt(record);
                          }}
                        >
                          {isReceiptBusy ? 'Working...' : 'Preview'}
                        </button>
                      )}
                      {onReceipt && (
                        <button
                          className="secondary"
                          disabled={isReceiptBusy}
                          onClick={(e) => {
                            e.stopPropagation();
                            onReceipt(record);
                          }}
                        >
                          {isReceiptBusy ? 'Working...' : 'Send'}
                        </button>
                      )}
                    </div>
                  </td>
                )}
              </tr>
              );
            })}
            {sortedRecords.length === 0 && (
              <tr>
                <td colSpan={7 + (showPayAction ? 1 : 0) + (onUpdate ? 1 : 0) + (onDelete ? 1 : 0) + (showTransaction ? 1 : 0) + (onRemind ? 1 : 0) + ((onReceipt || onPreviewReceipt) ? 1 : 0)} className="empty-state">No records found for selected range.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
};

export default React.memo(RentTable);
