export const DEFAULT_ROOM_OPTIONS = [
  'G1', 'G2', 'G3', 'G4',
  '101', '102', '103', '104', '105', '106',
  '201', '202', '203', '204', '205', '206',
  '301', '302', '303', '304', '305', '306',
  '401', '402', '403', '404', '405', '406'
];

export const mergeRoomOptions = (customOptions = []) => {
  const normalized = customOptions
    .map((room) => String(room || '').trim().toUpperCase())
    .filter(Boolean);

  return Array.from(new Set([...DEFAULT_ROOM_OPTIONS, ...normalized]));
};
