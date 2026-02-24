import React, { useEffect, useState } from 'react';
import { mergeRoomOptions } from '../constants/roomOptions';

const initialState = {
  fullName: '',
  tenantPhoneNumber: '',
  dailyAccommodation: false,
  dailyFoodOption: 'WITH_FOOD',
  dailyCollectionAmount: '',
  dailyCollectionTransactionDate: '',
  dailyStayDays: '1',
  roomNumber: '',
  rent: '',
  rentPaidAmount: '',
  deposit: '',
  depositPaidAmount: '',
  joiningCollectionAccountId: '',
  joiningDate: '',
  emergencyContactNumber: '',
  emergencyContactRelationship: '',
  sharing: 'DOUBLE',
  companyName: '',
  companyAddress: '',
  rentDueAmount: '0',
  verificationStatus: 'NOT_DONE'
};

const TenantForm = ({ selectedTenant, onSubmit, onCancel, roomOptions, accounts }) => {
  const [form, setForm] = useState(selectedTenant || initialState);
  const rooms = mergeRoomOptions(roomOptions);
  const accountList = accounts || [];

  useEffect(() => {
    setForm(selectedTenant ? { ...initialState, ...selectedTenant } : initialState);
  }, [selectedTenant]);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const toNonNegative = (value) => Math.max(Number(value || 0), 0);

  const rentValue = toNonNegative(form.rent);
  const depositValue = toNonNegative(form.deposit);
  const rentPaidValue = Math.min(toNonNegative(form.rentPaidAmount), rentValue);
  const depositPaidValue = Math.min(toNonNegative(form.depositPaidAmount), depositValue);
  const rentDueValue = Math.max(rentValue - rentPaidValue, 0);
  const paymentStatus = rentDueValue === 0 ? 'ON_TIME' : rentPaidValue > 0 ? 'PARTIAL' : 'DUE';
  const dailyMode = Boolean(form.dailyAccommodation);
  const dailyCollectionValue = toNonNegative(form.dailyCollectionAmount);
  const dailyStayDaysValue = Math.max(Number(form.dailyStayDays || 1), 1);

  const submitForm = (event) => {
    event.preventDefault();

    const normalizedPayload = dailyMode
      ? {
          ...form,
          deposit: 0,
          rentDueAmount: 0,
          rentPaidAmount: 0,
          depositPaidAmount: 0,
          paymentStatus: 'ON_TIME',
          dailyCollectionAmount: dailyCollectionValue,
          dailyCollectionTransactionDate: form.dailyCollectionTransactionDate || form.joiningDate || '',
          dailyCollectionAccountId: form.dailyCollectionAccountId ? Number(form.dailyCollectionAccountId) : null,
          dailyStayDays: dailyStayDaysValue
        }
      : {
          ...form,
          rent: rentValue,
          deposit: depositValue,
          rentDueAmount: rentDueValue,
          rentPaidAmount: rentPaidValue,
          depositPaidAmount: depositPaidValue,
          joiningCollectionAccountId: form.joiningCollectionAccountId ? Number(form.joiningCollectionAccountId) : null,
          paymentStatus,
          dailyFoodOption: null,
          dailyCollectionAmount: 0,
          dailyCollectionTransactionDate: null,
          dailyCollectionAccountId: null,
          dailyStayDays: null
        };

    onSubmit(
      normalizedPayload,
      selectedTenant?.id
    );

    if (!selectedTenant) {
      setForm(initialState);
    }
  };

  return (
    <form className="form" onSubmit={submitForm}>
      <h3>{selectedTenant ? 'Update Tenant' : 'Add Tenant'}</h3>
      <div className="form-grid">
        <input name="fullName" value={form.fullName} onChange={handleChange} placeholder="Full Name" required />
        <input
          name="tenantPhoneNumber"
          value={form.tenantPhoneNumber}
          onChange={handleChange}
          placeholder="Tenant Phone Number (10 digits)"
          inputMode="numeric"
          pattern="\d{10}"
          maxLength={10}
          required
        />
        <label className="checkbox-row">
          <input
            type="checkbox"
            name="dailyAccommodation"
            checked={Boolean(form.dailyAccommodation)}
            onChange={(e) => setForm((prev) => ({ ...prev, dailyAccommodation: e.target.checked }))}
          />
          <span>Daily Accommodation</span>
        </label>
        <select name="roomNumber" value={form.roomNumber} onChange={handleChange} required>
          <option value="">Select Room Number</option>
          {rooms.map((room) => (
            <option key={room} value={room}>
              {room}
            </option>
          ))}
        </select>
        {dailyMode && (
          <select name="dailyFoodOption" value={form.dailyFoodOption || 'WITH_FOOD'} onChange={handleChange}>
            <option value="WITH_FOOD">With Food</option>
            <option value="WITHOUT_FOOD">Without Food</option>
          </select>
        )}
        <input name="rent" type="number" value={form.rent} onChange={handleChange} placeholder="Total Rent" required />
        {!dailyMode && (
          <input
            name="rentPaidAmount"
            type="number"
            min="0"
            value={form.rentPaidAmount}
            onChange={handleChange}
            placeholder="Rent Paid Amount"
            required
          />
        )}
        {!dailyMode && (
          <input name="deposit" type="number" value={form.deposit} onChange={handleChange} placeholder="Total Deposit" required />
        )}
        {!dailyMode && (
          <input
            name="depositPaidAmount"
            type="number"
            min="0"
            value={form.depositPaidAmount}
            onChange={handleChange}
            placeholder="Deposit Paid Amount"
            required
          />
        )}
        {!dailyMode && (
          <select
            name="joiningCollectionAccountId"
            value={form.joiningCollectionAccountId || ''}
            onChange={handleChange}
          >
            <option value="">Select Collection Account (Optional)</option>
            {accountList.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name} ({account.mode})
              </option>
            ))}
          </select>
        )}
        {dailyMode && (
          <input
            name="dailyCollectionAmount"
            type="number"
            min="0"
            value={form.dailyCollectionAmount}
            onChange={handleChange}
            placeholder="Daily Collection Amount"
            required
          />
        )}
        {dailyMode && (
          <input
            name="dailyCollectionTransactionDate"
            type="date"
            value={form.dailyCollectionTransactionDate || ''}
            onChange={handleChange}
            placeholder="Collection Transaction Date"
          />
        )}
        {dailyMode && (
          <select
            name="dailyCollectionAccountId"
            value={form.dailyCollectionAccountId || ''}
            onChange={handleChange}
          >
            <option value="">Select Collection Account (Optional)</option>
            {accountList.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name} ({account.mode})
              </option>
            ))}
          </select>
        )}
        {dailyMode && (
          <input
            name="dailyStayDays"
            type="number"
            min="1"
            value={form.dailyStayDays}
            onChange={handleChange}
            placeholder="How Many Days Stay"
            required
          />
        )}
        <input name="joiningDate" type="date" value={form.joiningDate} onChange={handleChange} required />
        <input
          name="emergencyContactNumber"
          value={form.emergencyContactNumber}
          onChange={handleChange}
          placeholder="Emergency Contact Number (optional)"
          inputMode="numeric"
          pattern="\d{10}"
          maxLength={10}
        />
        <input
          name="emergencyContactRelationship"
          value={form.emergencyContactRelationship}
          onChange={handleChange}
          placeholder="Emergency Contact Name (optional)"
        />
        <input name="companyName" value={form.companyName} onChange={handleChange} placeholder="Company Name" />
        <input name="companyAddress" value={form.companyAddress} onChange={handleChange} placeholder="Company Address" />
        <select name="verificationStatus" value={form.verificationStatus} onChange={handleChange}>
          <option value="NOT_DONE">Verification Not Done</option>
          <option value="DONE">Verification Done</option>
        </select>
      </div>
      <div className="inline-hint">
        Auto calculated on save: Rent Due = Total Rent - Rent Paid.
      </div>
      <div className="form-actions">
        <button type="submit">{selectedTenant ? 'Update' : 'Create Tenant'}</button>
        {selectedTenant && (
          <button type="button" className="secondary" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
};

export default TenantForm;
