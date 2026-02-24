import React, { useMemo, useState } from 'react';
import { DEFAULT_ROOM_OPTIONS } from '../constants/roomOptions';

const FLOOR_CONFIG = [
  { key: 'GROUND', label: 'Ground Floor' },
  { key: '1', label: '1st Floor' },
  { key: '2', label: '2nd Floor' },
  { key: '3', label: '3rd Floor' },
  { key: '4', label: '4th Floor' }
];

const floorKeyForRoom = (roomNumber = '') => {
  const normalized = String(roomNumber || '').trim().toUpperCase();
  if (normalized.startsWith('G')) return 'GROUND';
  if (normalized.startsWith('1')) return '1';
  if (normalized.startsWith('2')) return '2';
  if (normalized.startsWith('3')) return '3';
  if (normalized.startsWith('4')) return '4';
  return '';
};

const roomSortValue = (roomNumber = '') => {
  const normalized = String(roomNumber || '').toUpperCase();
  if (normalized.startsWith('G')) {
    return Number(normalized.replace('G', '')) || 0;
  }
  return Number(normalized) || 0;
};

const RoomManager = ({ rooms, tenants, onCreateOrUpdate, onDelete, onOpenTenant }) => {
  const [form, setForm] = useState({ roomNumber: '', bedCapacity: 1 });
  const [editingRoomId, setEditingRoomId] = useState(null);
  const [expandedRoomId, setExpandedRoomId] = useState(null);
  const [selectedFloor, setSelectedFloor] = useState('GROUND');

  const tenantsByRoom = useMemo(() => {
    const map = new Map();
    tenants.forEach((tenant) => {
      const key = String(tenant.roomNumber || '').toUpperCase();
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(tenant);
    });
    return map;
  }, [tenants]);

  const roomsByFloor = useMemo(() => {
    const grouped = new Map(FLOOR_CONFIG.map((floor) => [floor.key, []]));
    rooms.forEach((room) => {
      const key = floorKeyForRoom(room.roomNumber);
      if (grouped.has(key)) {
        grouped.get(key).push(room);
      }
    });
    grouped.forEach((value) => {
      value.sort((a, b) => roomSortValue(a.roomNumber) - roomSortValue(b.roomNumber));
    });
    return grouped;
  }, [rooms]);

  const floorSummaries = useMemo(() => {
    const summaries = new Map();
    FLOOR_CONFIG.forEach((floor) => {
      const floorRooms = roomsByFloor.get(floor.key) || [];
      const totals = floorRooms.reduce(
        (acc, room) => {
          const beds = Number(room.bedCapacity || 0);
          const occupants = tenantsByRoom.get(String(room.roomNumber || '').toUpperCase()) || [];
          const occupied = Math.min(occupants.length, beds);
          acc.totalBeds += beds;
          acc.occupiedBeds += occupied;
          acc.vacantBeds += Math.max(beds - occupied, 0);
          return acc;
        },
        { rooms: floorRooms.length, totalBeds: 0, occupiedBeds: 0, vacantBeds: 0 }
      );
      summaries.set(floor.key, totals);
    });
    return summaries;
  }, [roomsByFloor, tenantsByRoom]);

  const visibleRooms = roomsByFloor.get(selectedFloor) || [];

  const submit = (event) => {
    event.preventDefault();
    onCreateOrUpdate(
      {
        roomNumber: String(form.roomNumber || '').trim().toUpperCase(),
        bedCapacity: Number(form.bedCapacity || 1)
      },
      editingRoomId
    );
    setForm({ roomNumber: '', bedCapacity: 1 });
    setEditingRoomId(null);
  };

  return (
    <>
      <form className="form" onSubmit={submit}>
        <h3>{editingRoomId ? 'Update Room' : 'Add Room'}</h3>
        <div className="form-grid">
          <select
            required
            value={form.roomNumber}
            onChange={(e) => setForm((prev) => ({ ...prev, roomNumber: e.target.value }))}
          >
            <option value="">Select Room Number</option>
            {DEFAULT_ROOM_OPTIONS.map((room) => (
              <option key={room} value={room}>
                {room}
              </option>
            ))}
          </select>
          <select
            value={form.bedCapacity}
            onChange={(e) => setForm((prev) => ({ ...prev, bedCapacity: Number(e.target.value) }))}
          >
            <option value={1}>Single (1 bed)</option>
            <option value={2}>2 Sharing (2 beds)</option>
            <option value={3}>3 Sharing (3 beds)</option>
            <option value={4}>4 Sharing (4 beds)</option>
          </select>
        </div>
        <div className="form-actions">
          <button type="submit">{editingRoomId ? 'Update Room' : 'Add Room'}</button>
          {editingRoomId && (
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setEditingRoomId(null);
                setForm({ roomNumber: '', bedCapacity: 1 });
              }}
            >
              Cancel
            </button>
          )}
        </div>
      </form>

      <section className="section-panel">
        <h3>Room Inventory</h3>
        <div className="floor-switcher">
          {FLOOR_CONFIG.map((floor) => {
            const summary = floorSummaries.get(floor.key) || { rooms: 0, totalBeds: 0, occupiedBeds: 0, vacantBeds: 0 };
            const active = selectedFloor === floor.key;
            return (
              <button
                key={floor.key}
                type="button"
                className={`floor-tab ${active ? 'active' : ''}`}
                onClick={() => {
                  setSelectedFloor(floor.key);
                  setExpandedRoomId(null);
                }}
              >
                <strong>{floor.label}</strong>
                <span>Rooms {summary.rooms}</span>
                <span>Beds {summary.totalBeds}</span>
                <span>Occupied {summary.occupiedBeds}</span>
                <span>Vacant {summary.vacantBeds}</span>
              </button>
            );
          })}
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Room</th>
                <th>Beds</th>
                <th>Occupied</th>
                <th>Vacant</th>
                <th>Expand</th>
                <th>Edit</th>
                <th>Delete</th>
              </tr>
            </thead>
            <tbody>
              {visibleRooms.map((room) => {
                const occupants = tenantsByRoom.get(room.roomNumber) || [];
                const occupied = Math.min(occupants.length, Number(room.bedCapacity || 0));
                const vacant = Math.max(Number(room.bedCapacity || 0) - occupied, 0);
                const expanded = expandedRoomId === room.id;
                return (
                  <React.Fragment key={room.id}>
                    <tr>
                      <td data-label="Room">{room.roomNumber}</td>
                      <td data-label="Beds">{room.bedCapacity}</td>
                      <td data-label="Occupied">
                        {occupied > 0 ? (
                          <button
                            type="button"
                            className="secondary"
                            onClick={() => onOpenTenant?.(occupants[0]?.id)}
                          >
                            {occupied}
                          </button>
                        ) : (
                          occupied
                        )}
                      </td>
                      <td data-label="Vacant">{vacant}</td>
                      <td data-label="Expand">
                        <button
                          className="secondary"
                          onClick={() => setExpandedRoomId((prev) => (prev === room.id ? null : room.id))}
                        >
                          {expanded ? 'Hide' : 'View'}
                        </button>
                      </td>
                      <td data-label="Edit">
                        <button
                          className="secondary"
                          onClick={() => {
                            setEditingRoomId(room.id);
                            setForm({ roomNumber: room.roomNumber, bedCapacity: room.bedCapacity });
                          }}
                        >
                          Edit
                        </button>
                      </td>
                      <td data-label="Delete">
                        <button className="danger" onClick={() => onDelete(room.id)}>
                          Delete
                        </button>
                      </td>
                    </tr>
                    {expanded && (
                      <tr>
                        <td colSpan="7">
                          <div className="room-expand">
                            {Array.from({ length: Number(room.bedCapacity || 0) }).map((_, index) => {
                              const occupant = occupants[index];
                              return (
                                <div key={`${room.id}-${index}`} className={`room-bed ${occupant ? 'occupied' : 'vacant'}`}>
                                  <strong>Bed {index + 1}</strong>
                                  {occupant ? (
                                    <button
                                      type="button"
                                      className="secondary"
                                      onClick={() => onOpenTenant?.(occupant.id)}
                                    >
                                      {occupant.fullName}
                                    </button>
                                  ) : (
                                    <span>Vacant</span>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
              {visibleRooms.length === 0 && (
                <tr>
                  <td colSpan="7" className="empty-state">No rooms in this floor.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
};

export default RoomManager;
