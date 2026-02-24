const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
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
}

export const tenantApi = {
  list: async () => {
    try {
      return await request('/tenants/active');
    } catch (error) {
      return request('/tenants');
    }
  },
  listDaily: () => request('/tenants/daily'),
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
  delete: (id) => request(`/tenants/${id}`, { method: 'DELETE' }),
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
