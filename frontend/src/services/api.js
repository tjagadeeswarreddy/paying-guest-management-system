const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const GET_CACHE_TTL_MS = Number(import.meta.env.VITE_API_GET_CACHE_TTL_MS || 15000);
const ENABLE_DEV_STATS = Boolean(import.meta.env.DEV);
const getCache = new Map();
const inFlightGets = new Map();
const apiStats = {
  networkCalls: 0,
  cacheHits: 0,
  dedupedHits: 0,
  writes: 0
};
let statsLogTimer = null;

const isGetMethod = (method) => String(method || 'GET').toUpperCase() === 'GET';

const clearGetCache = () => {
  getCache.clear();
  inFlightGets.clear();
};

const scheduleStatsLog = () => {
  if (!ENABLE_DEV_STATS) return;
  if (statsLogTimer) return;
  statsLogTimer = setTimeout(() => {
    // eslint-disable-next-line no-console
    console.info(
      `[api-stats] network=${apiStats.networkCalls} cacheHits=${apiStats.cacheHits} deduped=${apiStats.dedupedHits} writes=${apiStats.writes}`
    );
    statsLogTimer = null;
  }, 1200);
};

async function request(path, options = {}) {
  const method = String(options.method || 'GET').toUpperCase();
  const cacheKey = `${method}:${path}`;
  const now = Date.now();

  if (isGetMethod(method)) {
    const cached = getCache.get(cacheKey);
    if (cached && cached.expiresAt > now) {
      apiStats.cacheHits += 1;
      scheduleStatsLog();
      return cached.value;
    }
    if (inFlightGets.has(cacheKey)) {
      apiStats.dedupedHits += 1;
      scheduleStatsLog();
      return inFlightGets.get(cacheKey);
    }
  }

  const fetchPromise = (async () => {
    apiStats.networkCalls += 1;
    scheduleStatsLog();
    const response = await fetch(`${BASE_URL}${path}`, {
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {})
      },
      ...options
    });

    if (!response.ok) {
      const message = await response.text();
      throw new Error(message || 'Request failed');
    }

    if (response.status === 204) {
      return null;
    }

    return response.json();
  })();

  if (isGetMethod(method)) {
    inFlightGets.set(cacheKey, fetchPromise);
  }

  try {
    const result = await fetchPromise;
    if (isGetMethod(method)) {
      getCache.set(cacheKey, {
        value: result,
        expiresAt: now + Math.max(GET_CACHE_TTL_MS, 1000)
      });
    } else {
      apiStats.writes += 1;
      clearGetCache();
    }
    scheduleStatsLog();
    return result;
  } finally {
    if (isGetMethod(method)) {
      inFlightGets.delete(cacheKey);
    }
  }
}

export const tenantApi = {
  list: async () => {
    const tenants = await request('/tenants');
    return (tenants || []).filter((tenant) => tenant?.active && !tenant?.dailyAccommodation);
  },
  listDaily: async () => {
    const tenants = await request('/tenants');
    return (tenants || []).filter((tenant) => tenant?.active && tenant?.dailyAccommodation);
  },
  listAll: async () => {
    try {
      return await request('/tenants?includeInactive=true');
    } catch (firstError) {
      try {
        return await request('/tenants/all');
      } catch (secondError) {
        return request('/tenants');
      }
    }
  },
  listDeleted: () => request('/tenants/deleted'),
  delete: (id) => request(`/tenants/${id}`, { method: 'DELETE' }),
  restore: (id) => request(`/tenants/${id}/restore`, { method: 'PATCH' }),
  deletePermanent: async (id) => {
    const response = await fetch(`${BASE_URL}/tenants/${id}/permanent`, {
      method: 'DELETE',
      cache: 'no-store',
      headers: { 'Content-Type': 'application/json' }
    });
    // Idempotent UX: if already removed, keep UI consistent and refresh lists.
    if (response.status === 404 || response.status === 204) {
      return null;
    }
    if (!response.ok) {
      throw new Error((await response.text()) || 'Request failed');
    }
    clearGetCache();
    return null;
  },
  checkout: (id) => request(`/tenants/${id}/checkout`, { method: 'PATCH' }),
  deleteDailyCollection: (id) => request(`/tenants/${id}/daily-collection`, { method: 'DELETE' }),
  createOrUpdate: async (payload, tenantId = null) => {
    const path = `/tenants${tenantId ? `/${tenantId}` : ''}`;
    const method = tenantId ? 'PUT' : 'POST';

    const jsonResponse = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (jsonResponse.ok) {
      clearGetCache();
      return jsonResponse.json();
    }

    if (jsonResponse.status === 415) {
      const formData = new FormData();
      formData.append(
        'payload',
        new Blob([JSON.stringify(payload)], { type: 'application/json' })
      );

      const multipartResponse = await fetch(`${BASE_URL}${path}`, {
        method,
        body: formData
      });

      if (!multipartResponse.ok) {
        throw new Error(await multipartResponse.text());
      }
      clearGetCache();
      return multipartResponse.json();
    }

    throw new Error(await jsonResponse.text());
  }
};

export const rentApi = {
  upsert: (payload) =>
    request('/rents', {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  update: (id, payload) =>
    request(`/rents/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    }),
  delete: (id) => request(`/rents/${id}`, { method: 'DELETE' }),
  deleteCollected: (id) => request(`/rents/collected/${id}`, { method: 'DELETE' }),
  markPaid: (id) => request(`/rents/${id}/pay`, { method: 'PATCH' }),
  due: (from, to) => request(`/rents/due?from=${from}&to=${to}`),
  collected: (from, to) => request(`/rents/collected?from=${from}&to=${to}`),
  dashboard: (from, to) => request(`/rents/dashboard?from=${from}&to=${to}`),
  transactions: (id) => request(`/rents/${id}/transactions`),
  exportCollectedCsv: async (from, to, accountId = null) => {
    const query = new URLSearchParams({
      from,
      to,
      ...(accountId ? { accountId: String(accountId) } : {})
    });
    const response = await fetch(`${BASE_URL}/rents/collected/export?${query.toString()}`);
    if (!response.ok) {
      throw new Error(await response.text());
    }
    return response.blob();
  }
};

export const roomApi = {
  list: () => request('/rooms'),
  createOrUpdate: (payload, roomId = null) =>
    request(`/rooms${roomId ? `/${roomId}` : ''}`, {
      method: roomId ? 'PUT' : 'POST',
      body: JSON.stringify(payload)
    }),
  delete: (id) => request(`/rooms/${id}`, { method: 'DELETE' })
};

export const accountApi = {
  list: () => request('/accounts'),
  createOrUpdate: (payload, accountId = null) =>
    request(`/accounts${accountId ? `/${accountId}` : ''}`, {
      method: accountId ? 'PUT' : 'POST',
      body: JSON.stringify(payload)
    }),
  delete: (id) => request(`/accounts/${id}`, { method: 'DELETE' })
};

export const expenseApi = {
  list: () => request('/expenses'),
  createOrUpdate: (payload, expenseId = null) =>
    request(`/expenses${expenseId ? `/${expenseId}` : ''}`, {
      method: expenseId ? 'PUT' : 'POST',
      body: JSON.stringify(payload)
    }),
  delete: (id) => request(`/expenses/${id}`, { method: 'DELETE' })
};
