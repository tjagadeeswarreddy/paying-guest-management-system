import React, { useEffect, useMemo, useState } from 'react';
import { BedDouble, CalendarClock, ChevronLeft, ChevronRight, CircleDollarSign, Landmark, LayoutDashboard, Menu, PlusSquare, Users, WalletCards, X } from 'lucide-react';
import AccountsManager from './components/AccountsManager';
import DailyTenantTable from './components/DailyTenantTable';
import DashboardCards from './components/DashboardCards';
import RentEntryForm from './components/RentEntryForm';
import RentTable from './components/RentTable';
import RoomManager from './components/RoomManager';
import TenantCards from './components/TenantCards';
import TenantForm from './components/TenantForm';
import { accountApi, rentApi, roomApi, tenantApi } from './services/api';
import { toCurrency } from './utils/format';

const tabs = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Add Tenant', icon: PlusSquare },
  { label: 'Tenants', icon: Users },
  { label: 'Daily Tenants', icon: CalendarClock },
  { label: 'Rooms', icon: BedDouble },
  { label: 'Due Rents', icon: WalletCards },
  { label: 'Collected Rents', icon: CircleDollarSign },
  { label: 'Accounts', icon: Landmark }
];

const toLocalInputDate = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const currentMonthRange = () => {
  const now = new Date();
  return {
    from: toLocalInputDate(new Date(now.getFullYear(), now.getMonth(), 1)),
    to: toLocalInputDate(new Date(now.getFullYear(), now.getMonth() + 1, 0))
  };
};

const lastMonthRange = () => {
  const now = new Date();
  return {
    from: toLocalInputDate(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
    to: toLocalInputDate(new Date(now.getFullYear(), now.getMonth(), 0))
  };
};

const monthRangeByOffset = (offset = 0) => {
  const now = new Date();
  return {
    from: toLocalInputDate(new Date(now.getFullYear(), now.getMonth() - offset, 1)),
    to: toLocalInputDate(new Date(now.getFullYear(), now.getMonth() - offset + 1, 0))
  };
};

const monthLabel = (dateValue) => {
  if (!dateValue) return '-';
  const date = new Date(`${dateValue}T00:00:00`);
  return date.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
};

const dateInRange = (dateValue, from, to) => {
  if (!dateValue) return false;
  if (!from || !to) return true;
  return dateValue >= from && dateValue <= to;
};

const App = () => {
  const [activeTab, setActiveTab] = useState('Dashboard');
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const [tenants, setTenants] = useState([]);
  const [allTenants, setAllTenants] = useState([]);
  const [dailyTenants, setDailyTenants] = useState([]);
  const [deletedTenantHistory, setDeletedTenantHistory] = useState([]);
  const [dueRecords, setDueRecords] = useState([]);
  const [collectedRecords, setCollectedRecords] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [focusedTenantId, setFocusedTenantId] = useState(null);

  const [selectedTenant, setSelectedTenant] = useState(null);

  const [dueRange, setDueRange] = useState(currentMonthRange());
  const [collectedRange, setCollectedRange] = useState(currentMonthRange());
  const [collectedMonthOffset, setCollectedMonthOffset] = useState(0);
  const [collectedAccountFilter, setCollectedAccountFilter] = useState('ALL');
  const [showDailyCollectionDetails, setShowDailyCollectionDetails] = useState(true);

  const [loading, setLoading] = useState(false);

  const topbarSubtitleByTab = {
    Dashboard: 'Corporate-ready PG management with clean workflows and structured records.',
    'Add Tenant': 'Create and maintain tenant profiles with clear financial details.',
    Tenants: 'View and manage active tenant records.',
    'Daily Tenants': 'Track daily accommodation tenants and their day-wise collections.',
    Accounts: 'Manage bank/cash/UPI accounts and tag collections correctly.',
    'Due Rents': 'Track outstanding dues and update payments quickly.',
    'Collected Rents': 'Collection ledger with transaction dates and totals.'
  };

  const withLoading = async (fn) => {
    try {
      setLoading(true);
      await fn();
    } catch (error) {
      let message = error?.message || 'Request failed. Please check backend/API configuration.';
      try {
        const parsed = JSON.parse(message);
        message = parsed?.message || parsed?.error || message;
      } catch (parseError) {
        // Keep raw message when it is not JSON.
      }
      // eslint-disable-next-line no-alert
      alert(message);
    } finally {
      setLoading(false);
    }
  };

  const refreshAll = async () => {
    await Promise.all([fetchTenants(), fetchDailyTenants(), fetchAllTenants(), fetchDueRecords(), fetchCollectedRecords(), fetchRooms(), fetchAccounts()]);
  };

  const fetchTenants = async () => {
    const data = await tenantApi.list();
    setTenants(data);
  };

  const fetchAllTenants = async () => {
    const data = await tenantApi.listAll();
    setAllTenants(data);
  };

  const fetchDailyTenants = async () => {
    try {
      const data = await tenantApi.listDaily();
      setDailyTenants(data);
    } catch (error) {
      setDailyTenants([]);
    }
  };

  const fetchDueRecords = async () => {
    const data = await rentApi.due(dueRange.from, dueRange.to);
    setDueRecords(data);
  };

  const fetchCollectedRecords = async () => {
    const data = await rentApi.collected(collectedRange.from, collectedRange.to);
    setCollectedRecords(data);
  };

  const fetchRooms = async () => {
    try {
      const data = await roomApi.list();
      setRooms(data);
    } catch (error) {
      setRooms([]);
    }
  };

  const fetchAccounts = async () => {
    try {
      const data = await accountApi.list();
      setAccounts(data);
    } catch (error) {
      setAccounts([]);
    }
  };

  useEffect(() => {
    withLoading(refreshAll);
  }, []);

  useEffect(() => {
    withLoading(fetchDueRecords);
  }, [dueRange.from, dueRange.to]);

  useEffect(() => {
    withLoading(fetchCollectedRecords);
  }, [collectedRange.from, collectedRange.to]);

  useEffect(() => {
    if (collectedMonthOffset === null) return;
    setCollectedRange(monthRangeByOffset(collectedMonthOffset));
  }, [collectedMonthOffset]);

  const ledgerTenants = useMemo(() => {
    const ids = new Set(allTenants.map((tenant) => tenant.id));
    const missingHistory = deletedTenantHistory.filter((tenant) => !ids.has(tenant.id));
    return [...allTenants, ...missingHistory];
  }, [allTenants, deletedTenantHistory]);

  const combinedCollectionRecords = useMemo(() => {
    return collectedRecords.map((record) => ({
      ...record,
      collectionType: 'RENT_RECORD'
    }));
  }, [collectedRecords]);

  const combinedDueRecords = useMemo(() => {
    return dueRecords.map((record) => ({
      ...record,
      collectionType: 'RENT_RECORD'
    }));
  }, [dueRecords]);

  const dueSummary = useMemo(() => {
    const outstanding = combinedDueRecords.reduce(
      (sum, record) => sum + Math.max(Number(record.dueAmount || 0) - Number(record.paidAmount || 0), 0),
      0
    );
    return {
      totalOutstanding: outstanding
    };
  }, [combinedDueRecords]);

  const dailyCollectionEntries = useMemo(
    () =>
      allTenants
        .filter((tenant) => tenant.dailyAccommodation && Number(tenant.dailyCollectionAmount || 0) > 0)
        .map((tenant) => ({
          id: tenant.id,
          tenantName: tenant.fullName,
          roomNumber: tenant.roomNumber,
          dailyFoodOption: tenant.dailyFoodOption,
          dailyStayDays: tenant.dailyStayDays,
          amount: Number(tenant.dailyCollectionAmount || 0),
          accountId: tenant.dailyCollectionAccountId,
          accountName: tenant.dailyCollectionAccountName,
          transactionDate: tenant.dailyCollectionTransactionDate || tenant.joiningDate || '',
          checkoutDate: tenant.checkoutDate,
          joiningDate: tenant.joiningDate
        }))
        .filter((entry) => dateInRange(entry.transactionDate, collectedRange.from, collectedRange.to)),
    [allTenants, collectedRange.from, collectedRange.to]
  );

  const dashboardSummary = useMemo(() => {
    const monthlyCollection = combinedCollectionRecords.reduce(
      (sum, record) => sum + Number(record.paidAmount || 0),
      0
    );
    const totalDailyCollection = dailyCollectionEntries.reduce(
      (sum, entry) => sum + Number(entry.amount || 0),
      0
    );
    const totalCollection = monthlyCollection + totalDailyCollection;

    const dueTenantIds = new Set(combinedDueRecords.map((record) => record.tenantId));
    const partialTenantIds = new Set(
      combinedDueRecords
        .filter((record) => String(record.status || '').toUpperCase() === 'PARTIAL')
        .map((record) => record.tenantId)
    );
    const collectedTenantIds = new Set(combinedCollectionRecords.map((record) => record.tenantId));
    const paidTenantCount = [...collectedTenantIds].filter((tenantId) => !dueTenantIds.has(tenantId)).length;
    const occupantsByRoom = new Map();
    tenants.forEach((tenant) => {
      const roomNumber = String(tenant.roomNumber || '').toUpperCase();
      occupantsByRoom.set(roomNumber, (occupantsByRoom.get(roomNumber) || 0) + 1);
    });
    const totalBeds = rooms.reduce((sum, room) => sum + Number(room.bedCapacity || 0), 0);
    const occupiedBeds = rooms.reduce((sum, room) => {
      const occupants = occupantsByRoom.get(String(room.roomNumber || '').toUpperCase()) || 0;
      return sum + Math.min(occupants, Number(room.bedCapacity || 0));
    }, 0);

    return {
      totalRentCollection: totalCollection,
      totalDailyCollection,
      totalDueAmount: dueSummary.totalOutstanding,
      totalPendingCollection: dueSummary.totalOutstanding,
      activeTenants: tenants.length,
      dailyTenants: dailyTenants.length,
      paidTenantsThisMonth: paidTenantCount,
      dueTenantsThisMonth: dueTenantIds.size,
      partialTenantsThisMonth: partialTenantIds.size,
      totalBeds,
      occupiedBeds,
      vacantBeds: Math.max(totalBeds - occupiedBeds, 0)
    };
  }, [combinedCollectionRecords, combinedDueRecords, dailyCollectionEntries, dailyTenants, dueSummary.totalOutstanding, rooms, tenants]);

  const totalDailyCollection = useMemo(
    () => dailyCollectionEntries.reduce((sum, entry) => sum + entry.amount, 0),
    [dailyCollectionEntries]
  );

  const filteredCollectionRecords = useMemo(() => {
    if (collectedAccountFilter === 'ALL') {
      return combinedCollectionRecords;
    }
    return combinedCollectionRecords.filter(
      (record) => String(record.accountId || '') === String(collectedAccountFilter)
    );
  }, [collectedAccountFilter, combinedCollectionRecords]);

  const filteredDailyCollectionEntries = useMemo(() => {
    if (collectedAccountFilter === 'ALL') {
      return dailyCollectionEntries;
    }
    return dailyCollectionEntries.filter(
      (entry) => String(entry.accountId || '') === String(collectedAccountFilter)
    );
  }, [collectedAccountFilter, dailyCollectionEntries]);

  const filteredDailyCollectionTotal = useMemo(
    () => filteredDailyCollectionEntries.reduce((sum, entry) => sum + entry.amount, 0),
    [filteredDailyCollectionEntries]
  );

  const collectedTotal = useMemo(
    () =>
      filteredCollectionRecords.reduce((sum, record) => sum + Number(record.paidAmount || 0), 0)
      + filteredDailyCollectionTotal,
    [filteredCollectionRecords, filteredDailyCollectionTotal]
  );

  const applyCurrentMonth = (setter) => setter(currentMonthRange());
  const applyLastMonth = (setter) => setter(lastMonthRange());

  const handleTenantSubmit = async (payload, tenantId) => {
    await withLoading(async () => {
      await tenantApi.createOrUpdate(payload, tenantId);
      if (tenantId) {
        setDeletedTenantHistory((prev) => prev.filter((tenant) => tenant.id !== tenantId));
      }
      setSelectedTenant(null);
      await refreshAll();
      setActiveTab(payload.dailyAccommodation ? 'Daily Tenants' : 'Tenants');
    });
  };

  const handleTenantDelete = async (id) => {
    const snapshot = ledgerTenants.find((tenant) => tenant.id === id);
    await withLoading(async () => {
      await tenantApi.delete(id);
      if (snapshot) {
        setDeletedTenantHistory((prev) => {
          const existingIndex = prev.findIndex((tenant) => tenant.id === id);
          const next = { ...snapshot, active: false };
          if (existingIndex >= 0) {
            const copy = [...prev];
            copy[existingIndex] = next;
            return copy;
          }
          return [...prev, next];
        });
      }
      await refreshAll();
    });
  };

  const handleDailyCheckout = async (id) => {
    await withLoading(async () => {
      await tenantApi.checkout(id);
      await refreshAll();
    });
  };

  const handleDeleteDailyCollection = async (tenantId) => {
    await withLoading(async () => {
      await tenantApi.deleteDailyCollection(tenantId);
      await refreshAll();
    });
  };

  const handleEditTenant = (tenant) => {
    setSelectedTenant(tenant);
    setActiveTab('Add Tenant');
  };

  const handleRentUpdate = async (payload) => {
    await withLoading(async () => {
      await rentApi.upsert(payload);
      await refreshAll();
    });
  };

  const handleMarkPaid = async (recordRef, payment) => {
    await withLoading(async () => {
      const currentDue = Number(recordRef.dueAmount || 0);
      const currentPaid = Number(recordRef.paidAmount || 0);
      const balance = Math.max(currentDue - currentPaid, 0);
      const transactionDate = payment?.transactionDate || null;
      const accountId = payment?.accountId ?? null;

      if (payment?.mode === 'PARTIAL') {
        const additionalPaid = Math.min(Math.max(Number(payment.amount || 0), 0), balance);
        const nextPaid = currentPaid + additionalPaid;
        await rentApi.update(recordRef.id, {
          dueAmount: currentDue,
          paidAmount: nextPaid,
          transactionDate,
          accountId
        });
      } else {
        await rentApi.update(recordRef.id, {
          dueAmount: currentDue,
          paidAmount: currentDue,
          transactionDate,
          accountId
        });
      }
      await refreshAll();
    });
  };

  const handleUpdateRentRecord = async (recordRef, payload) => {
    await withLoading(async () => {
      const normalizedPayload = recordRef.collectionType === 'RENT_RECORD'
        ? {
            dueAmount: Number(payload.dueAmount || 0) + Number(payload.paidAmount || 0),
            paidAmount: Number(payload.paidAmount || 0),
            accountId: payload.accountId ?? null
          }
        : payload;
      await rentApi.update(recordRef.id, normalizedPayload);
      await refreshAll();
    });
  };

  const handleDeleteDueRent = async (record) => {
    await withLoading(async () => {
      await rentApi.delete(record.id);
      await refreshAll();
    });
  };

  const handleDeleteCollectedRent = async (record) => {
    await withLoading(async () => {
      await rentApi.deleteCollected(record.id);
      await refreshAll();
    });
  };

  const handleRoomSave = async (payload, roomId) => {
    await withLoading(async () => {
      await roomApi.createOrUpdate(payload, roomId);
      await refreshAll();
      setActiveTab('Rooms');
    });
  };

  const handleRoomDelete = async (roomId) => {
    await withLoading(async () => {
      await roomApi.delete(roomId);
      await refreshAll();
    });
  };

  const handleOpenTenantFromRooms = (tenantId) => {
    if (!tenantId) return;
    setFocusedTenantId(tenantId);
    setActiveTab('Tenants');
  };

  const handleAccountSave = async (payload, accountId) => {
    await withLoading(async () => {
      await accountApi.createOrUpdate(payload, accountId);
      await refreshAll();
      setActiveTab('Accounts');
    });
  };

  const handleAccountDelete = async (accountId) => {
    await withLoading(async () => {
      await accountApi.delete(accountId);
      await refreshAll();
    });
  };

  const handleExportCollectedCsv = async () => {
    await withLoading(async () => {
      const accountId = collectedAccountFilter === 'ALL' ? null : Number(collectedAccountFilter);
      const blob = await rentApi.exportCollectedCsv(collectedRange.from, collectedRange.to, accountId);
      const fileLabel = accountId
        ? `collections-${collectedRange.from}-to-${collectedRange.to}-account-${accountId}.csv`
        : `collections-${collectedRange.from}-to-${collectedRange.to}-all-accounts.csv`;
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = fileLabel;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
    });
  };

  const renderDateFilter = (label, range, setRange) => (
    <section className="section-panel filter-panel">
      <div className="filter-header-row">
        <h3>{label}</h3>
        <div className="quick-filters">
          <button type="button" className="secondary" onClick={() => applyCurrentMonth(setRange)}>
            Current Month
          </button>
          <button type="button" className="secondary" onClick={() => applyLastMonth(setRange)}>
            Last Month
          </button>
        </div>
      </div>
      <div className="filter-bar standard-filter">
        <input
          type="date"
          value={range.from}
          onChange={(e) => setRange((prev) => ({ ...prev, from: e.target.value }))}
        />
        <input
          type="date"
          value={range.to}
          onChange={(e) => setRange((prev) => ({ ...prev, to: e.target.value }))}
        />
      </div>
    </section>
  );

  const renderCollectedFilter = () => (
    <section className="section-panel filter-panel">
      <div className="filter-header-row">
        <h3>Collected Rent Range</h3>
        <div className="quick-filters">
          <button type="button" className="secondary" onClick={() => setCollectedMonthOffset(0)}>
            Current Month
          </button>
          <button type="button" className="secondary" onClick={() => setCollectedMonthOffset(1)}>
            Last Month
          </button>
          <button type="button" className="secondary" onClick={handleExportCollectedCsv}>
            Download CSV
          </button>
        </div>
      </div>
      <div className="filter-header-row">
        <div className="actions">
          <button type="button" className="secondary" onClick={() => setCollectedMonthOffset((prev) => (prev ?? 0) + 1)}>
            <ChevronLeft size={14} />
          </button>
          <button
            type="button"
            className="secondary"
            onClick={() => setCollectedMonthOffset((prev) => Math.max((prev ?? 0) - 1, 0))}
            disabled={(collectedMonthOffset ?? 0) === 0}
          >
            <ChevronRight size={14} />
          </button>
        </div>
        <p>
          Showing: {monthLabel(collectedRange.from)}
        </p>
      </div>
      <div className="filter-bar standard-filter">
        <select
          value={collectedAccountFilter}
          onChange={(e) => setCollectedAccountFilter(e.target.value)}
        >
          <option value="ALL">All Accounts</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name} ({account.mode})
            </option>
          ))}
        </select>
      </div>
      <div className="filter-bar standard-filter">
        <input
          type="date"
          value={collectedRange.from}
          onChange={(e) => {
            setCollectedMonthOffset(null);
            setCollectedRange((prev) => ({ ...prev, from: e.target.value }));
          }}
        />
        <input
          type="date"
          value={collectedRange.to}
          onChange={(e) => {
            setCollectedMonthOffset(null);
            setCollectedRange((prev) => ({ ...prev, to: e.target.value }));
          }}
        />
      </div>
    </section>
  );

  return (
    <div className={`layout-shell ${mobileNavOpen ? 'sidebar-open' : ''}`}>
      <div
        className={`mobile-sidebar-backdrop ${mobileNavOpen ? 'show' : ''}`}
        onClick={() => setMobileNavOpen(false)}
      />
      <aside className={`sidebar ${mobileNavOpen ? 'open' : ''}`}>
        <div className="brand-block">
          <p className="brand-kicker">PG Operations</p>
          <h1>Stay Ledger</h1>
          <p>Manage tenants, dues, and collections in one place.</p>
        </div>
        <nav className="sidebar-nav">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.label}
                className={`side-tab ${activeTab === tab.label ? 'active' : ''}`}
                onClick={() => {
                  if (tab.label !== 'Add Tenant') {
                    setSelectedTenant(null);
                  }
                  setActiveTab(tab.label);
                  setMobileNavOpen(false);
                }}
              >
                <span className="tab-content">
                  <Icon size={16} />
                  <span>{tab.label}</span>
                </span>
              </button>
            );
          })}
        </nav>
      </aside>

      <div className="content-shell">
        <header className="topbar">
          <div className="topbar-head">
            <button
              type="button"
              className="mobile-menu-button"
              onClick={() => setMobileNavOpen((prev) => !prev)}
              aria-label={mobileNavOpen ? 'Close menu' : 'Open menu'}
            >
              {mobileNavOpen ? <X size={18} /> : <Menu size={18} />}
            </button>
            <div>
              <h2>{activeTab}</h2>
              <p>{topbarSubtitleByTab[activeTab] || ''}</p>
            </div>
          </div>
        </header>

        <main>
          {loading && <section className="section-panel">Loading latest records...</section>}

          {!loading && activeTab === 'Dashboard' && (
            <>
              <section className="section-panel">
                <h3>Dashboard Totals</h3>
                <p>Computed from currently displayed rows in Due Rents and Collection Rents tables.</p>
              </section>
              <DashboardCards summary={dashboardSummary} />
              <RentEntryForm tenants={tenants} accounts={accounts} onSubmit={handleRentUpdate} />
            </>
          )}

          {!loading && activeTab === 'Add Tenant' && (
            <TenantForm
              selectedTenant={selectedTenant}
              roomOptions={rooms.map((room) => room.roomNumber)}
              accounts={accounts}
              onSubmit={handleTenantSubmit}
              onCancel={() => setSelectedTenant(null)}
            />
          )}

          {!loading && activeTab === 'Tenants' && (
            <TenantCards
              tenants={tenants}
              onEdit={handleEditTenant}
              onDelete={handleTenantDelete}
              focusedTenantId={focusedTenantId}
            />
          )}

          {!loading && activeTab === 'Daily Tenants' && (
            <DailyTenantTable
              tenants={dailyTenants}
              onEdit={handleEditTenant}
              onCheckout={handleDailyCheckout}
              onDelete={handleTenantDelete}
            />
          )}

          {!loading && activeTab === 'Rooms' && (
            <RoomManager
              rooms={rooms}
              tenants={tenants}
              onCreateOrUpdate={handleRoomSave}
              onDelete={handleRoomDelete}
              onOpenTenant={handleOpenTenantFromRooms}
            />
          )}

          {!loading && activeTab === 'Accounts' && (
            <AccountsManager
              accounts={accounts}
              onSave={handleAccountSave}
              onDelete={handleAccountDelete}
            />
          )}

          {!loading && activeTab === 'Due Rents' && (
            <>
              {renderDateFilter('Due Rent Range', dueRange, setDueRange)}
              <section className="cards-grid due-summary-grid">
                <article className="metric-card">
                  <p>Total Outstanding Due</p>
                  <h3>{toCurrency(dueSummary.totalOutstanding)}</h3>
                </article>
              </section>
              <RentTable
                title="Outstanding Dues"
                records={combinedDueRecords}
                showPayAction
                onPay={handleMarkPaid}
                onUpdate={handleUpdateRentRecord}
                onDelete={handleDeleteDueRent}
                showTransaction={false}
                dateLabel="Due Date"
                enableSort
                accounts={accounts}
              />
            </>
          )}

          {!loading && activeTab === 'Collected Rents' && (
            <>
              {renderCollectedFilter()}
              <section className="cards-grid due-summary-grid">
                <article className="metric-card">
                  <p>Total Collected</p>
                  <h3>{toCurrency(collectedTotal)}</h3>
                </article>
                <article className="metric-card">
                  <p>Daily Collection (Selected Month)</p>
                  <h3>{toCurrency(filteredDailyCollectionTotal)}</h3>
                </article>
              </section>
              <section className="section-panel filter-panel">
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={showDailyCollectionDetails}
                    onChange={(e) => setShowDailyCollectionDetails(e.target.checked)}
                  />
                  <span>Show Daily Tenant Collection Details</span>
                </label>
              </section>
              <RentTable
                title="Collected Rents"
                records={filteredCollectionRecords}
                showPayAction={false}
                onUpdate={handleUpdateRentRecord}
                onDelete={handleDeleteCollectedRent}
                showTransaction
                dateLabel="Due Date"
                accounts={accounts}
              />
              {showDailyCollectionDetails && (
                <section className="section-panel">
                  <h3>Daily Tenant Collection (Including Checked-Out)</h3>
                  <div className="tenant-rows-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Name</th>
                          <th>Room</th>
                          <th>Food</th>
                          <th>Stay Days</th>
                          <th>Collected</th>
                          <th>Account</th>
                          <th>Txn Date</th>
                          <th>Joined</th>
                          <th>Checkout</th>
                          <th>Delete</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredDailyCollectionEntries.map((entry) => (
                          <tr key={`daily-col-${entry.id}`}>
                            <td data-label="Name">{entry.tenantName}</td>
                            <td data-label="Room">{entry.roomNumber}</td>
                            <td data-label="Food">{entry.dailyFoodOption || '-'}</td>
                            <td data-label="Stay Days">{entry.dailyStayDays || 1}</td>
                            <td data-label="Collected">{toCurrency(entry.amount)}</td>
                            <td data-label="Account">{entry.accountName || '-'}</td>
                            <td data-label="Txn Date">{entry.transactionDate || '-'}</td>
                            <td data-label="Joined">{entry.joiningDate || '-'}</td>
                            <td data-label="Checkout">{entry.checkoutDate || '-'}</td>
                            <td data-label="Delete">
                              <button className="danger" onClick={() => handleDeleteDailyCollection(entry.id)}>
                                Delete
                              </button>
                            </td>
                          </tr>
                        ))}
                        {filteredDailyCollectionEntries.length === 0 && (
                          <tr>
                            <td colSpan="10" className="empty-state">No daily collection records found for selected range.</td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
};

export default App;
