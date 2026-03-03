import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BedDouble, CalendarClock, ChevronLeft, ChevronRight, CircleDollarSign, Landmark, LayoutDashboard, Menu, PlusSquare, ReceiptText, Trash2, Users, WalletCards, X } from 'lucide-react';
import AccountsManager from './components/AccountsManager';
import DailyTenantTable from './components/DailyTenantTable';
import DashboardCards from './components/DashboardCards';
import DeletedTenantsTable from './components/DeletedTenantsTable';
import ExpensesManager from './components/ExpensesManager';
import RentTable from './components/RentTable';
import RoomManager from './components/RoomManager';
import TenantCards from './components/TenantCards';
import TenantForm from './components/TenantForm';
import { accountApi, expenseApi, rentApi, roomApi, tenantApi } from './services/api';
import { toCurrency } from './utils/format';
import { createReceiptPdfBlob } from './utils/receiptPdf';

const tabs = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Add Tenant', icon: PlusSquare },
  { label: 'Tenants', icon: Users },
  { label: 'Daily Tenants', icon: CalendarClock },
  { label: 'Rooms', icon: BedDouble },
  { label: 'Due Rents', icon: WalletCards },
  { label: 'Collected Rents', icon: CircleDollarSign },
  { label: 'Expenses', icon: ReceiptText },
  { label: 'Accounts', icon: Landmark },
  { label: 'Deleted Tenants', icon: Trash2 }
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

const currentBillingMonthStart = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
};

const monthStartFromDate = (dateValue) => {
  if (!dateValue || String(dateValue).length < 7) return currentBillingMonthStart();
  return `${String(dateValue).slice(0, 7)}-01`;
};

const formatWhatsappPhone = (phone) => {
  const digits = String(phone || '').replace(/\D/g, '');
  if (!digits) return null;
  if (digits.length === 10) return `91${digits}`;
  if (digits.length === 11 && digits.startsWith('0')) return `91${digits.slice(1)}`;
  if (digits.length >= 11 && digits.length <= 15) return digits;
  return null;
};

const formatIstDate = (value) =>
  new Date(value).toLocaleDateString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    year: 'numeric'
  });

const formatIstDateTime = (value) =>
  new Date(value).toLocaleString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });

const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
};

const RECEIPT_COUNTER_KEY = 'slv_receipt_counter_v1';
const RECEIPT_NUMBER_CACHE_KEY = 'slv_receipt_number_cache_v1';
const RECEIPT_BUSINESS_NAME = import.meta.env.VITE_RECEIPT_BUSINESS_NAME || 'PG Management';
const RECEIPT_BUSINESS_ADDRESS = import.meta.env.VITE_RECEIPT_BUSINESS_ADDRESS || 'Address not configured';
const RECEIPT_BUSINESS_PHONE = import.meta.env.VITE_RECEIPT_BUSINESS_PHONE || '';
const DEFAULT_REMINDER_PAY_NUMBER = import.meta.env.VITE_REMINDER_PAY_NUMBER || '';

const App = () => {
  const isFirebaseMode = (import.meta.env.VITE_APP_DATA_PROVIDER || '').toLowerCase() === 'firebase';
  const [activeTab, setActiveTab] = useState('Dashboard');
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const [tenants, setTenants] = useState([]);
  const [allTenants, setAllTenants] = useState([]);
  const [dailyTenants, setDailyTenants] = useState([]);
  const [deletedTenants, setDeletedTenants] = useState([]);
  const [dueRecords, setDueRecords] = useState([]);
  const [collectedRecords, setCollectedRecords] = useState([]);
  const [currentMonthCollectedRecords, setCurrentMonthCollectedRecords] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [focusedTenantId, setFocusedTenantId] = useState(null);

  const [selectedTenant, setSelectedTenant] = useState(null);

  const [dueRange, setDueRange] = useState(currentMonthRange());
  const [collectedRange, setCollectedRange] = useState(currentMonthRange());
  const [collectedMonthOffset, setCollectedMonthOffset] = useState(0);
  const [collectedAccountFilter, setCollectedAccountFilter] = useState('ALL');
  const [showDailyCollectionDetails, setShowDailyCollectionDetails] = useState(true);
  const [showVacantRoomDetails, setShowVacantRoomDetails] = useState(false);
  const [reminderPayNumber, setReminderPayNumber] = useState(DEFAULT_REMINDER_PAY_NUMBER);
  const [receiptBusyKey, setReceiptBusyKey] = useState(null);
  const receiptActionLocksRef = useRef(new Set());

  const [loading, setLoading] = useState(false);
  const skipInitialDueFetch = useRef(true);
  const skipInitialCollectedFetch = useRef(true);
  const firebaseUnsupportedTabs = useMemo(() => new Set(), []);

  const topbarSubtitleByTab = {
    Dashboard: 'Corporate-ready PG management with clean workflows and structured records.',
    'Add Tenant': 'Create and maintain tenant profiles with clear financial details.',
    Tenants: 'View and manage active tenant records.',
    'Daily Tenants': 'Track daily accommodation tenants and their day-wise collections.',
    'Deleted Tenants': 'Restore soft-deleted tenants or remove records permanently.',
    Expenses: 'Track daily expenses and link them to specific accounts.',
    Accounts: 'Manage bank/cash/UPI accounts and tag collections correctly.',
    'Due Rents': 'Track outstanding dues and update payments quickly.',
    'Collected Rents': 'Collection ledger with transaction dates and totals.'
  };

  const withLoading = async (fn, options = {}) => {
    const showSpinner = options.showSpinner !== false;
    try {
      if (showSpinner) setLoading(true);
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
      if (showSpinner) setLoading(false);
    }
  };

  const applyTenantSnapshots = (tenantRows = []) => {
    const rows = tenantRows || [];
    const nextTenants = [];
    const nextDailyTenants = [];
    const nextDeletedTenants = [];
    for (const tenant of rows) {
      if (!tenant?.active) {
        nextDeletedTenants.push(tenant);
      } else if (tenant?.dailyAccommodation) {
        nextDailyTenants.push(tenant);
      } else {
        nextTenants.push(tenant);
      }
    }
    setAllTenants(rows);
    setTenants(nextTenants);
    setDailyTenants(nextDailyTenants);
    setDeletedTenants(nextDeletedTenants);
  };

  const tenantsById = useMemo(() => {
    const map = new Map();
    (allTenants || []).forEach((tenant) => {
      map.set(String(tenant.id), tenant);
    });
    return map;
  }, [allTenants]);

  const accountsById = useMemo(() => {
    const map = new Map();
    (accounts || []).forEach((account) => {
      map.set(String(account.id), account);
    });
    return map;
  }, [accounts]);

  const activeTenants = useMemo(
    () => (allTenants || []).filter((tenant) => tenant?.active),
    [allTenants]
  );

  const fetchTenantSnapshots = async () => {
    const data = await tenantApi.listAll();
    applyTenantSnapshots(data);
    return data;
  };

  const refreshAll = async () => {
    await Promise.all([
      fetchTenantSnapshots(),
      fetchRooms(),
      fetchAccounts(),
      fetchDueRecords(),
      fetchCollectedRecords(),
      fetchCurrentMonthCollectedRecords()
    ]);
  };

  const refreshTenantLists = async () => {
    await fetchTenantSnapshots();
  };

  const refreshDueAndTenantLists = async () => {
    await Promise.all([fetchDueRecords(), fetchTenantSnapshots(), fetchCurrentMonthCollectedRecords()]);
  };

  const refreshFinancialLists = async () => {
    await Promise.all([
      fetchDueRecords(),
      fetchCollectedRecords(),
      fetchTenantSnapshots(),
      fetchCurrentMonthCollectedRecords()
    ]);
  };


  const fetchDueRecords = async () => {
    const data = await rentApi.due(dueRange.from, dueRange.to);
    setDueRecords(data);
  };

  const fetchCollectedRecords = async () => {
    const data = await rentApi.collected(collectedRange.from, collectedRange.to);
    setCollectedRecords(data);
  };

  const fetchCurrentMonthCollectedRecords = async () => {
    const range = currentMonthRange();
    const data = await rentApi.collected(range.from, range.to);
    setCurrentMonthCollectedRecords(data);
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

  const fetchExpenses = async () => {
    try {
      const data = await expenseApi.list();
      setExpenses(data);
    } catch (error) {
      setExpenses([]);
    }
  };

  useEffect(() => {
    withLoading(refreshAll);
  }, []);

  useEffect(() => {
    if (skipInitialDueFetch.current) {
      skipInitialDueFetch.current = false;
      return;
    }
    withLoading(fetchDueRecords, { showSpinner: false });
  }, [dueRange.from, dueRange.to]);

  useEffect(() => {
    if (skipInitialCollectedFetch.current) {
      skipInitialCollectedFetch.current = false;
      return;
    }
    withLoading(fetchCollectedRecords, { showSpinner: false });
  }, [collectedRange.from, collectedRange.to]);

  useEffect(() => {
    if (collectedMonthOffset === null) return;
    setCollectedRange(monthRangeByOffset(collectedMonthOffset));
  }, [collectedMonthOffset]);

  useEffect(() => {
    if (activeTab !== 'Collected Rents') return;
    withLoading(async () => {
      await Promise.all([
        fetchCollectedRecords(),
        ...(allTenants.length === 0 ? [fetchTenantSnapshots()] : [])
      ]);
    }, { showSpinner: false });
  }, [activeTab, allTenants.length]);

  useEffect(() => {
    if (activeTab !== 'Due Rents') return;
    withLoading(async () => {
      await Promise.all([
        fetchDueRecords(),
        ...(allTenants.length === 0 ? [fetchTenantSnapshots()] : [])
      ]);
    }, { showSpinner: false });
  }, [activeTab, allTenants.length]);

  useEffect(() => {
    if (activeTab !== 'Deleted Tenants') return;
    withLoading(fetchTenantSnapshots, { showSpinner: false });
  }, [activeTab]);

  useEffect(() => {
    if (activeTab !== 'Expenses') return;
    withLoading(fetchExpenses, { showSpinner: false });
  }, [activeTab]);

  const combinedCollectionRecords = useMemo(() => {
    const rentCollections = collectedRecords.map((record) => ({
      ...record,
      collectionType: 'RENT_RECORD'
    }));

    const joiningCollections = allTenants
      .filter((tenant) => !tenant.dailyAccommodation)
      .filter((tenant) => dateInRange(tenant.joiningDate, collectedRange.from, collectedRange.to))
      .map((tenant) => {
        const paidAmount = Number(tenant.rentPaidAmount || 0) + Number(tenant.depositPaidAmount || 0);
        if (paidAmount <= 0) return null;
        const account = accountsById.get(String(tenant.joiningCollectionAccountId));
        return {
          id: `joining-${tenant.id}`,
          tenantId: tenant.id,
          tenantName: tenant.fullName,
          roomNumber: tenant.roomNumber,
          billingMonth: tenant.joiningDate,
          transactionAt: tenant.joiningDate ? `${tenant.joiningDate}T00:00:00Z` : null,
          dueAmount: paidAmount,
          paidAmount,
          status: 'PAID',
          accountId: tenant.joiningCollectionAccountId || null,
          accountName: tenant.joiningCollectionAccountName || account?.name || null,
          collectionType: 'JOINING_COLLECTION'
        };
      })
      .filter(Boolean);

    return [...rentCollections, ...joiningCollections];
  }, [accountsById, allTenants, collectedRange.from, collectedRange.to, collectedRecords]);

  const combinedDueRecords = useMemo(() => {
    const rentDueRecords = dueRecords.map((record) => ({
      ...record,
      collectionType: 'RENT_RECORD'
    }));

    const existingDueKeys = new Set(
      rentDueRecords.map((record) => `${record.tenantId}-${record.billingMonth}`)
    );

    const joiningDueRows = allTenants
      .filter((tenant) => !tenant.dailyAccommodation)
      .filter((tenant) => dateInRange(tenant.joiningDate, dueRange.from, dueRange.to))
      .map((tenant) => {
        const depositPending = Math.max(
          Number(tenant.deposit || 0) - Number(tenant.depositPaidAmount || 0),
          0
        );
        const dueAmount = Math.max(Number(tenant.rentDueAmount || 0), 0) + depositPending;
        if (dueAmount <= 0) return null;

        const billingMonth = tenant.joiningDate
          ? `${tenant.joiningDate.slice(0, 7)}-01`
          : tenant.joiningDate;
        const key = `${tenant.id}-${billingMonth}`;
        if (existingDueKeys.has(key)) return null;

        return {
          id: `joining-due-${tenant.id}`,
          tenantId: tenant.id,
          tenantName: tenant.fullName,
          roomNumber: tenant.roomNumber,
          billingMonth,
          transactionAt: tenant.joiningDate ? `${tenant.joiningDate}T00:00:00Z` : null,
          dueAmount,
          paidAmount: 0,
          status: 'DUE',
          accountId: tenant.joiningCollectionAccountId || null,
          accountName: tenant.joiningCollectionAccountName || null,
          collectionType: 'JOINING_DUE'
        };
      })
      .filter(Boolean);

    return [...rentDueRecords, ...joiningDueRows];
  }, [allTenants, dueRange.from, dueRange.to, dueRecords]);

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
    const activeOccupants = allTenants.filter((tenant) => tenant?.active && tenant?.roomNumber);
    activeOccupants.forEach((tenant) => {
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
  }, [allTenants, combinedCollectionRecords, combinedDueRecords, dailyCollectionEntries, dailyTenants, dueSummary.totalOutstanding, rooms, tenants.length]);

  const vacantRooms = useMemo(() => {
    const occupantsByRoom = new Map();
    allTenants
      .filter((tenant) => tenant?.active && tenant?.roomNumber)
      .forEach((tenant) => {
        const roomKey = String(tenant.roomNumber || '').toUpperCase();
        occupantsByRoom.set(roomKey, (occupantsByRoom.get(roomKey) || 0) + 1);
      });

    return rooms
      .map((room) => {
        const roomKey = String(room.roomNumber || '').toUpperCase();
        const totalBeds = Number(room.bedCapacity || 0);
        const occupiedBeds = Math.min(occupantsByRoom.get(roomKey) || 0, totalBeds);
        const vacantBeds = Math.max(totalBeds - occupiedBeds, 0);
        return {
          roomNumber: room.roomNumber,
          totalBeds,
          occupiedBeds,
          vacantBeds
        };
      })
      .filter((room) => room.vacantBeds > 0)
      .sort((a, b) => String(a.roomNumber || '').localeCompare(String(b.roomNumber || '')));
  }, [allTenants, rooms]);

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

  const tenantPaidByCurrentMonth = useMemo(() => {
    const totals = {};
    (currentMonthCollectedRecords || []).forEach((record) => {
      const tenantId = record?.tenantId;
      if (tenantId == null) return;
      totals[tenantId] = Number(totals[tenantId] || 0) + Number(record?.paidAmount || 0);
    });
    return totals;
  }, [currentMonthCollectedRecords]);

  const applyCurrentMonth = useCallback((setter) => setter(currentMonthRange()), []);
  const applyLastMonth = useCallback((setter) => setter(lastMonthRange()), []);

  const findTenantById = useCallback((tenantId) => tenantsById.get(String(tenantId)), [tenantsById]);

  const roomOptions = useMemo(() => rooms.map((room) => room.roomNumber), [rooms]);

  const toTenantUpsertPayload = (tenant, overrides = {}) => ({
    fullName: tenant.fullName || '',
    tenantPhoneNumber: tenant.tenantPhoneNumber || '',
    dailyAccommodation: Boolean(tenant.dailyAccommodation),
    dailyFoodOption: tenant.dailyFoodOption || null,
    dailyCollectionAmount: Number(tenant.dailyCollectionAmount || 0),
    dailyCollectionTransactionDate: tenant.dailyCollectionTransactionDate || null,
    dailyCollectionAccountId: tenant.dailyCollectionAccountId ?? null,
    dailyStayDays: tenant.dailyStayDays ?? null,
    roomNumber: tenant.roomNumber || '',
    rent: Number(tenant.rent || 0),
    deposit: Number(tenant.deposit || 0),
    joiningDate: tenant.joiningDate || null,
    emergencyContactNumber: tenant.emergencyContactNumber || '',
    emergencyContactRelationship: tenant.emergencyContactRelationship || '',
    sharing: tenant.sharing || 'SINGLE',
    paymentStatus: tenant.paymentStatus || 'DUE',
    companyName: tenant.companyName || null,
    companyAddress: tenant.companyAddress || null,
    rentDueAmount: Number(tenant.rentDueAmount || 0),
    rentPaidAmount: Number(tenant.rentPaidAmount || 0),
    depositPaidAmount: Number(tenant.depositPaidAmount || 0),
    joiningCollectionAccountId: tenant.joiningCollectionAccountId ?? null,
    verificationStatus: tenant.verificationStatus || 'NOT_DONE',
    ...overrides
  });

  const updateJoiningTenantFinancials = async (tenantId, totalPaid, accountId = null) => {
    const tenant = findTenantById(tenantId);
    if (!tenant) {
      throw new Error(`Tenant not found for id: ${tenantId}`);
    }

    const rent = Math.max(Number(tenant.rent || 0), 0);
    const deposit = Math.max(Number(tenant.deposit || 0), 0);
    const requiredTotal = rent + deposit;
    const normalizedPaid = Math.min(Math.max(Number(totalPaid || 0), 0), requiredTotal);
    const rentPaidAmount = Math.min(normalizedPaid, rent);
    const depositPaidAmount = Math.min(Math.max(normalizedPaid - rent, 0), deposit);
    const rentDueAmount = Math.max(rent - rentPaidAmount, 0);
    const paymentStatus = rentDueAmount === 0 ? 'ON_TIME' : (rentPaidAmount > 0 ? 'PARTIAL' : 'DUE');

    await tenantApi.createOrUpdate(
      toTenantUpsertPayload(tenant, {
        rentPaidAmount,
        depositPaidAmount,
        rentDueAmount,
        paymentStatus,
        joiningCollectionAccountId: normalizedPaid > 0 ? (accountId ?? tenant.joiningCollectionAccountId ?? null) : null
      }),
      tenant.id
    );
  };

  const handleTenantSubmit = async (payload, tenantId) => {
    await withLoading(async () => {
      await tenantApi.createOrUpdate(payload, tenantId);
      setSelectedTenant(null);
      await refreshTenantLists();
      setActiveTab(payload.dailyAccommodation ? 'Daily Tenants' : 'Tenants');
    });
  };

  const handleTenantDelete = async (id) => {
    await withLoading(async () => {
      await tenantApi.delete(id);
      await refreshTenantLists();
    });
  };

  const handleRestoreTenant = async (id) => {
    await withLoading(async () => {
      await tenantApi.restore(id);
      await refreshTenantLists();
      setActiveTab('Tenants');
    });
  };

  const handlePermanentDeleteTenant = async (id) => {
    await withLoading(async () => {
      await tenantApi.deletePermanent(id);
      await refreshTenantLists();
    });
  };

  const handleDailyCheckout = async (id) => {
    await withLoading(async () => {
      await tenantApi.checkout(id);
      await refreshTenantLists();
    });
  };

  const handleDeleteDailyCollection = async (tenantId) => {
    await withLoading(async () => {
      await tenantApi.deleteDailyCollection(tenantId);
      await refreshTenantLists();
    });
  };

  const handleAddTenantDue = async (tenant, amount, dueDate) => {
    await withLoading(async () => {
      const today = toLocalInputDate(new Date());
      if (!dueDate || dueDate > today) {
        throw new Error('Due date should be today or a past date.');
      }
      const billingMonth = monthStartFromDate(dueDate);
      const existing = dueRecords.find(
        (record) =>
          String(record.tenantId) === String(tenant.id)
          && String(record.billingMonth || '').slice(0, 10) === billingMonth
      );

      if (existing) {
        await rentApi.update(existing.id, {
          dueAmount: Number(existing.dueAmount || 0) + Number(amount || 0),
          paidAmount: Number(existing.paidAmount || 0),
          accountId: existing.accountId ?? null
        });
      } else {
        await rentApi.upsert({
          tenantId: tenant.id,
          billingMonth,
          dueAmount: Number(amount || 0),
          paidAmount: 0,
          accountId: null
        });
      }
      await refreshDueAndTenantLists();
    });
  };

  const handleDueReminder = (record) => {
    const tenant = findTenantById(record.tenantId);
    const phone = formatWhatsappPhone(tenant?.tenantPhoneNumber);
    if (!phone) {
      // eslint-disable-next-line no-alert
      alert('Tenant phone number is missing or invalid for WhatsApp reminder.');
      return;
    }
    const dueBalance = Math.max(Number(record.dueAmount || 0) - Number(record.paidAmount || 0), 0);
    const monthLabelText = monthLabel(record.billingMonth);
    const payLine = reminderPayNumber
      ? `Please pay the rent to: ${String(reminderPayNumber).trim()}`
      : 'Please make the payment at the earliest.';
    const message = [
      `Hi ${record.tenantName || 'Tenant'},`,
      '',
      `This is a reminder for your pending PG rent.`,
      `Due Amount: ${toCurrency(dueBalance)}`,
      `Month: ${monthLabelText}`,
      '',
      payLine,
      'Kindly share the payment screenshot once done.',
      '',
      'Thank you.'
    ].join('\n');
    const url = `https://wa.me/${phone}?text=${encodeURIComponent(message)}`;
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const issueReceiptNumber = (record) => {
    const billingMonth = String(record?.billingMonth || '').slice(0, 7);
    const today = toLocalInputDate(new Date());
    const monthToken = (billingMonth || today.slice(0, 7)).replace('-', '');
    const year = monthToken.slice(0, 4);
    const month = monthToken.slice(4, 6);
    const cacheKey = `${record.collectionType || 'RENT_RECORD'}-${record.id}-${billingMonth || today.slice(0, 7)}`;
    try {
      const counterRaw = window.localStorage.getItem(RECEIPT_COUNTER_KEY);
      const cacheRaw = window.localStorage.getItem(RECEIPT_NUMBER_CACHE_KEY);
      const counter = counterRaw ? JSON.parse(counterRaw) : {};
      const cache = cacheRaw ? JSON.parse(cacheRaw) : {};
      if (cache[cacheKey]) return String(cache[cacheKey]);
      const next = Number(counter[monthToken] || 0) + 1;
      counter[monthToken] = next;
      const receiptNumber = `SLV/${year}/${month}/${String(next).padStart(4, '0')}`;
      cache[cacheKey] = receiptNumber;
      window.localStorage.setItem(RECEIPT_COUNTER_KEY, JSON.stringify(counter));
      window.localStorage.setItem(RECEIPT_NUMBER_CACHE_KEY, JSON.stringify(cache));
      return receiptNumber;
    } catch (error) {
      return `SLV/${year}/${month}/0001`;
    }
  };

  const buildReceiptPackage = (record, { requirePhone = true } = {}) => {
    const tenant = findTenantById(record.tenantId);
    const tenantName = record.tenantName || tenant?.fullName || '';
    const roomNumber = record.roomNumber || tenant?.roomNumber || '';
    const paidAmount = Number(record.paidAmount || 0);
    if (!tenantName) throw new Error('Tenant name is missing for receipt.');
    if (!roomNumber) throw new Error('Room number is missing for receipt.');
    if (paidAmount <= 0) throw new Error('Paid amount should be greater than zero for receipt.');
    const phone = formatWhatsappPhone(tenant?.tenantPhoneNumber);
    if (requirePhone && !phone) throw new Error('Tenant phone number is missing or invalid for WhatsApp receipt.');
    const now = new Date();
    const receiptNumber = issueReceiptNumber(record);
    const monthText = monthLabel(record.billingMonth);
    const amount = toCurrency(paidAmount);
    const amountForPdf = amount.replace(/₹/g, 'Rs. ');
    const transactionDate = record.transactionAt ? formatIstDate(record.transactionAt) : formatIstDate(now);
    const joiningDate = tenant?.joiningDate
      ? formatIstDate(`${tenant.joiningDate}T00:00:00`)
      : '-';
    const linkedAccount = accountsById.get(String(record.accountId || ''));
    const accountMode = String(linkedAccount?.mode || '').toUpperCase();
    const paymentType = accountMode === 'UPI' ? 'UPI' : 'Cash';

    const blob = createReceiptPdfBlob({
      businessName: RECEIPT_BUSINESS_NAME,
      businessAddress: RECEIPT_BUSINESS_ADDRESS,
      businessPhone: RECEIPT_BUSINESS_PHONE,
      receiptNumber,
      issuedAt: formatIstDateTime(now),
      tenantName,
      roomNumber,
      joiningDate,
      paymentType,
      billingMonthLabel: monthText,
      amountPaid: amountForPdf,
      paymentDate: transactionDate
    });

    const safeName = String(record.tenantName || 'tenant').trim().replace(/\s+/g, '-').toLowerCase();
    const filename = `receipt-${safeName}-${record.id}.pdf`;
    const message = [
      `Hi ${record.tenantName || tenant?.fullName || 'Tenant'},`,
      '',
      `Thank you for your rent payment of ${amount}.`,
      `Payment Type: ${paymentType}`,
      `Billing Month: ${monthText}`,
      `Receipt No: ${receiptNumber}`,
      '',
      'Please find your computer generated receipt attached.',
      '',
      'Thank you.'
    ].join('\n');

    return { blob, message, filename, phone, tenantName };
  };

  const handlePreviewReceipt = (record) => {
    const key = `${record.collectionType || 'RENT_RECORD'}-${record.id}`;
    if (receiptActionLocksRef.current.has(key)) return;
    receiptActionLocksRef.current.add(key);
    try {
      setReceiptBusyKey(key);
      const { blob, filename } = buildReceiptPackage(record, { requirePhone: false });
      const url = URL.createObjectURL(blob);
      const previewWindow = window.open(url, '_blank', 'noopener,noreferrer');
      if (!previewWindow) {
        downloadBlob(blob, filename);
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error) {
      // eslint-disable-next-line no-alert
      alert(error?.message || 'Unable to generate receipt preview.');
    } finally {
      receiptActionLocksRef.current.delete(key);
      setTimeout(() => setReceiptBusyKey((prev) => (prev === key ? null : prev)), 600);
    }
  };

  const handleSendReceipt = async (record) => {
    const key = `${record.collectionType || 'RENT_RECORD'}-${record.id}`;
    if (receiptActionLocksRef.current.has(key)) return;
    receiptActionLocksRef.current.add(key);
    setReceiptBusyKey(key);
    let pack;
    try {
      pack = buildReceiptPackage(record, { requirePhone: true });
    } catch (error) {
      // eslint-disable-next-line no-alert
      alert(error?.message || 'Unable to generate receipt.');
      receiptActionLocksRef.current.delete(key);
      setReceiptBusyKey((prev) => (prev === key ? null : prev));
      return;
    }
    const {
      blob, filename, message, phone, tenantName
    } = pack;

    try {
      try {
        const file = new File([blob], filename, { type: 'application/pdf' });
        if (navigator.share && navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({
            title: `Rent Receipt - ${tenantName || 'Tenant'}`,
            text: message,
            files: [file]
          });
          return;
        }
      } catch (error) {
        // Fallback below: download and open WhatsApp web/app.
      }

      downloadBlob(blob, filename);
      const fallbackMessage = [
        message,
        '',
        '(Receipt PDF downloaded. Please attach it from your files and send.)'
      ].join('\n');
      const url = `https://wa.me/${phone}?text=${encodeURIComponent(fallbackMessage)}`;
      window.open(url, '_blank', 'noopener,noreferrer');
      // eslint-disable-next-line no-alert
      alert('Receipt downloaded. Please attach the downloaded PDF in WhatsApp and send.');
    } finally {
      receiptActionLocksRef.current.delete(key);
      setTimeout(() => setReceiptBusyKey((prev) => (prev === key ? null : prev)), 600);
    }
  };

  const handleEditTenant = (tenant) => {
    setSelectedTenant(tenant);
    setActiveTab('Add Tenant');
  };

  const handleRentUpdate = async (payload) => {
    await withLoading(async () => {
      await rentApi.upsert(payload);
      await refreshDueAndTenantLists();
    });
  };

  const handleMarkPaid = async (recordRef, payment) => {
    await withLoading(async () => {
      const currentDue = Number(recordRef.dueAmount || 0);
      const currentPaid = Number(recordRef.paidAmount || 0);
      const balance = Math.max(currentDue - currentPaid, 0);
      const transactionDate = payment?.transactionDate || null;
      const accountId = payment?.accountId ?? null;

      if (recordRef.collectionType === 'RENT_RECORD') {
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
      } else {
        const tenant = findTenantById(recordRef.tenantId);
        const requiredTotal = Math.max(Number(tenant?.rent || 0), 0) + Math.max(Number(tenant?.deposit || 0), 0);
        if (payment?.mode === 'PARTIAL') {
          const additionalPaid = Math.min(Math.max(Number(payment.amount || 0), 0), balance);
          const nextPaid = currentPaid + additionalPaid;
          await updateJoiningTenantFinancials(recordRef.tenantId, nextPaid, accountId);
        } else {
          await updateJoiningTenantFinancials(recordRef.tenantId, requiredTotal, accountId);
        }
      }
      await refreshFinancialLists();
    });
  };

  const handleUpdateRentRecord = async (recordRef, payload) => {
    await withLoading(async () => {
      if (recordRef.collectionType === 'RENT_RECORD') {
        const normalizedPayload = {
          dueAmount: Number(payload.dueAmount || 0) + Number(payload.paidAmount || 0),
          paidAmount: Number(payload.paidAmount || 0),
          accountId: payload.accountId ?? null
        };
        await rentApi.update(recordRef.id, normalizedPayload);
      } else {
        const tenant = findTenantById(recordRef.tenantId);
        const requiredTotal = Math.max(Number(tenant?.rent || 0), 0) + Math.max(Number(tenant?.deposit || 0), 0);
        const due = Math.max(Number(payload.dueAmount || 0), 0);
        const inferredPaid = Math.max(requiredTotal - due, 0);
        const editedPaid = Math.max(Number(payload.paidAmount || 0), 0);
        const nextPaid = Math.min(Math.max(inferredPaid, editedPaid), requiredTotal);
        await updateJoiningTenantFinancials(recordRef.tenantId, nextPaid, payload.accountId ?? null);
      }
      await refreshFinancialLists();
    });
  };

  const handleDeleteDueRent = async (record) => {
    await withLoading(async () => {
      if (record.collectionType === 'RENT_RECORD') {
        await rentApi.delete(record.id);
      } else {
        const tenant = findTenantById(record.tenantId);
        const requiredTotal = Math.max(Number(tenant?.rent || 0), 0) + Math.max(Number(tenant?.deposit || 0), 0);
        await updateJoiningTenantFinancials(record.tenantId, requiredTotal, record.accountId ?? null);
      }
      await refreshFinancialLists();
    });
  };

  const handleDeleteCollectedRent = async (record) => {
    await withLoading(async () => {
      if (record.collectionType === 'RENT_RECORD') {
        await rentApi.deleteCollected(record.id);
      } else {
        await updateJoiningTenantFinancials(record.tenantId, 0, null);
      }
      await refreshFinancialLists();
    });
  };

  const handleRoomSave = async (payload, roomId) => {
    await withLoading(async () => {
      await roomApi.createOrUpdate(payload, roomId);
      await fetchRooms();
      setActiveTab('Rooms');
    });
  };

  const handleRoomDelete = async (roomId) => {
    await withLoading(async () => {
      await roomApi.delete(roomId);
      await fetchRooms();
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
      await fetchAccounts();
      setActiveTab('Accounts');
    });
  };

  const handleAccountDelete = async (accountId) => {
    await withLoading(async () => {
      await accountApi.delete(accountId);
      await Promise.all([
        fetchAccounts(),
        fetchTenantSnapshots(),
        ...(activeTab === 'Expenses' ? [fetchExpenses()] : [])
      ]);
    });
  };

  const handleExpenseSave = async (payload, expenseId) => {
    await withLoading(async () => {
      await expenseApi.createOrUpdate(payload, expenseId);
      await fetchExpenses();
      setActiveTab('Expenses');
    });
  };

  const handleExpenseDelete = async (expenseId) => {
    await withLoading(async () => {
      await expenseApi.delete(expenseId);
      await fetchExpenses();
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

          {!loading && isFirebaseMode && firebaseUnsupportedTabs.has(activeTab) && (
            <section className="section-panel">
              <h3>Feature not migrated yet</h3>
              <p>
                This module is still wired to PostgreSQL services. In Firebase mode, currently available modules are
                Add Tenant, Tenants, Daily Tenants, Rooms, and Accounts.
              </p>
            </section>
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Dashboard' && (
            <>
              <DashboardCards
                summary={dashboardSummary}
                onCardClick={(label) => {
                  if (label === 'Vacant Beds') {
                    setShowVacantRoomDetails((prev) => !prev);
                  }
                }}
              />
              {showVacantRoomDetails && (
                <section className="section-panel">
                  <h3>Vacant Beds By Room</h3>
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Room</th>
                          <th>Total Beds</th>
                          <th>Occupied</th>
                          <th>Vacant</th>
                        </tr>
                      </thead>
                      <tbody>
                        {vacantRooms.map((room) => (
                          <tr key={room.roomNumber}>
                            <td>{room.roomNumber}</td>
                            <td>{room.totalBeds}</td>
                            <td>{room.occupiedBeds}</td>
                            <td>{room.vacantBeds}</td>
                          </tr>
                        ))}
                        {vacantRooms.length === 0 && (
                          <tr>
                            <td colSpan="4" className="empty-state">No vacant beds right now.</td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}
            </>
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Add Tenant' && (
            <TenantForm
              selectedTenant={selectedTenant}
              roomOptions={roomOptions}
              accounts={accounts}
              onSubmit={handleTenantSubmit}
              onCancel={() => setSelectedTenant(null)}
            />
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Tenants' && (
            <TenantCards
              tenants={tenants}
              onEdit={handleEditTenant}
              onDelete={handleTenantDelete}
              focusedTenantId={focusedTenantId}
              onAddDue={handleAddTenantDue}
              paidByTenantId={tenantPaidByCurrentMonth}
            />
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Daily Tenants' && (
            <DailyTenantTable
              tenants={dailyTenants}
              onEdit={handleEditTenant}
              onCheckout={handleDailyCheckout}
              onDelete={handleTenantDelete}
            />
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Deleted Tenants' && (
            <DeletedTenantsTable
              tenants={deletedTenants}
              onRestore={handleRestoreTenant}
              onDeletePermanent={handlePermanentDeleteTenant}
            />
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Rooms' && (
            <RoomManager
              rooms={rooms}
              tenants={activeTenants}
              onCreateOrUpdate={handleRoomSave}
              onDelete={handleRoomDelete}
              onOpenTenant={handleOpenTenantFromRooms}
            />
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Accounts' && (
            <AccountsManager
              accounts={accounts}
              onSave={handleAccountSave}
              onDelete={handleAccountDelete}
            />
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Expenses' && (
            <ExpensesManager
              expenses={expenses}
              accounts={accounts}
              onSave={handleExpenseSave}
              onDelete={handleExpenseDelete}
            />
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Due Rents' && (
            <>
              {renderDateFilter('Due Rent Range', dueRange, setDueRange)}
              <section className="cards-grid due-summary-grid">
                <article className="metric-card">
                  <p>Total Outstanding Due</p>
                  <h3>{toCurrency(dueSummary.totalOutstanding)}</h3>
                </article>
              </section>
              <section className="section-panel filter-panel">
                <div className="filter-header-row">
                  <h3>Reminder Settings</h3>
                </div>
                <div className="filter-bar standard-filter">
                  <input
                    type="text"
                    value={reminderPayNumber}
                    onChange={(e) => setReminderPayNumber(e.target.value)}
                    placeholder="Payment Number for Reminder Message"
                  />
                </div>
              </section>
              <RentTable
                title="Outstanding Dues"
                records={combinedDueRecords}
                showPayAction
                onPay={handleMarkPaid}
                onUpdate={handleUpdateRentRecord}
                onDelete={handleDeleteDueRent}
                onRemind={handleDueReminder}
                showTransaction={false}
                dateLabel="Due Date"
                enableSort
                accounts={accounts}
              />
            </>
          )}

          {!loading && (!isFirebaseMode || !firebaseUnsupportedTabs.has(activeTab)) && activeTab === 'Collected Rents' && (
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
                onPreviewReceipt={handlePreviewReceipt}
                onReceipt={handleSendReceipt}
                receiptBusyKey={receiptBusyKey}
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
