const A4_WIDTH = 595;
const A4_HEIGHT = 620;
const PAGE_INNER_X = 38;
const PAGE_INNER_Y = 38;
const PAGE_INNER_WIDTH = A4_WIDTH - (PAGE_INNER_X * 2);
const PAGE_TOP_Y = A4_HEIGHT - PAGE_INNER_Y;

const ONES = [
  '', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine',
  'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen',
  'Seventeen', 'Eighteen', 'Nineteen'
];
const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

const escapePdfText = (value) =>
  String(value ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/\(/g, '\\(')
    .replace(/\)/g, '\\)')
    .replace(/\r?\n/g, ' ');

const formatOffset = (offset) => String(offset).padStart(10, '0');

const byteLength = (value) => new TextEncoder().encode(value).length;

const twoDigitWord = (num) => {
  if (num < 20) return ONES[num];
  const ten = Math.floor(num / 10);
  const one = num % 10;
  return `${TENS[ten]}${one ? ` ${ONES[one]}` : ''}`;
};

const threeDigitWord = (num) => {
  const hundred = Math.floor(num / 100);
  const rem = num % 100;
  let out = '';
  if (hundred) out += `${ONES[hundred]} Hundred`;
  if (rem) out += `${out ? ' ' : ''}${twoDigitWord(rem)}`;
  return out.trim();
};

const integerToIndianWords = (num) => {
  if (num === 0) return 'Zero';
  const parts = [];
  const crore = Math.floor(num / 10000000);
  const lakh = Math.floor((num % 10000000) / 100000);
  const thousand = Math.floor((num % 100000) / 1000);
  const last = num % 1000;
  if (crore) parts.push(`${twoDigitWord(crore)} Crore`);
  if (lakh) parts.push(`${twoDigitWord(lakh)} Lakh`);
  if (thousand) parts.push(`${twoDigitWord(thousand)} Thousand`);
  if (last) parts.push(threeDigitWord(last));
  return parts.join(' ').trim();
};

const wrapByWords = (text, maxChars = 84) => {
  const normalized = String(text || '').replace(/\s+/g, ' ').trim();
  if (!normalized) return ['-'];
  const words = normalized.split(' ');
  const lines = [];
  let line = '';
  words.forEach((word) => {
    const next = line ? `${line} ${word}` : word;
    if (next.length > maxChars && line) {
      lines.push(line);
      line = word;
      return;
    }
    line = next;
  });
  if (line) lines.push(line);
  return lines;
};

const drawText = (ops, { text, x, y, font = 'F1', size = 11 }) => {
  ops.push(`BT /${font} ${size} Tf ${x} ${y} Td (${escapePdfText(text)}) Tj ET`);
};

const drawWrappedText = (ops, { text, x, y, maxChars = 84, lineGap = 14, font = 'F1', size = 11 }) => {
  const lines = wrapByWords(text, maxChars);
  let cursorY = y;
  lines.forEach((line) => {
    drawText(ops, { text: line, x, y: cursorY, font, size });
    cursorY -= lineGap;
  });
  return cursorY;
};

const drawPair = (ops, { label, value, x, y, lineGap = 16 }) => {
  drawText(ops, { text: `${label}:`, x, y, font: 'F2', size: 11 });
  const nextY = drawWrappedText(ops, {
    text: value,
    x: x + 140,
    y,
    maxChars: 56,
    lineGap,
    font: 'F1',
    size: 11
  });
  return Math.min(y - lineGap, nextY);
};

export const toAmountWordsINR = (amountInput) => {
  const amount = Math.max(Number(amountInput || 0), 0);
  const rupees = Math.floor(amount);
  const paise = Math.round((amount - rupees) * 100);
  const rupeesWords = integerToIndianWords(rupees);
  if (paise <= 0) return `Rupees ${rupeesWords} Only`;
  const paiseWords = integerToIndianWords(paise);
  return `Rupees ${rupeesWords} and ${paiseWords} Paise Only`;
};

export const createReceiptPdfBlob = (receiptDetails) => {
  const {
    businessName,
    businessAddress,
    businessPhone,
    receiptNumber,
    issuedAt,
    tenantName,
    roomNumber,
    joiningDate,
    paymentType,
    billingMonthLabel,
    amountPaid,
    paymentDate
  } = receiptDetails;

  const ops = [];
  const topY = PAGE_TOP_Y - 16;
  let y = topY;

  ops.push('0.8 w');
  ops.push(`${PAGE_INNER_X} ${PAGE_INNER_Y} ${PAGE_INNER_WIDTH} ${A4_HEIGHT - (PAGE_INNER_Y * 2)} re S`);

  drawText(ops, { text: businessName || 'PG RENT RECEIPT', x: PAGE_INNER_X + 14, y, font: 'F2', size: 16 });
  y -= 20;
  y = drawWrappedText(ops, {
    text: businessAddress || '-',
    x: PAGE_INNER_X + 14,
    y,
    maxChars: 88,
    lineGap: 14,
    font: 'F1',
    size: 10
  });
  drawText(ops, {
    text: `Contact: ${businessPhone || '-'}`,
    x: PAGE_INNER_X + 14,
    y,
    font: 'F1',
    size: 10
  });
  y -= 18;

  ops.push('0.5 w');
  ops.push(`${PAGE_INNER_X + 14} ${y} m ${PAGE_INNER_X + PAGE_INNER_WIDTH - 14} ${y} l S`);
  y -= 24;

  ops.push('0.94 0.97 0.95 rg');
  ops.push(`${PAGE_INNER_X + 14} ${y - 22} ${PAGE_INNER_WIDTH - 28} 28 re f`);
  ops.push('0 g');
  drawText(ops, { text: 'RENT PAYMENT RECEIPT', x: PAGE_INNER_X + 14, y, font: 'F2', size: 14 });
  y -= 16;
  y = drawPair(ops, { label: 'Receipt No', value: receiptNumber || '-', x: PAGE_INNER_X + 14, y });
  y = drawPair(ops, { label: 'Issued On', value: issuedAt || '-', x: PAGE_INNER_X + 14, y });
  y -= 4;
  ops.push(`${PAGE_INNER_X + 14} ${y} m ${PAGE_INNER_X + PAGE_INNER_WIDTH - 14} ${y} l S`);
  y -= 16;

  y = drawPair(ops, { label: 'Tenant Name', value: tenantName || '-', x: PAGE_INNER_X + 14, y });
  y = drawPair(ops, { label: 'Room No', value: roomNumber || '-', x: PAGE_INNER_X + 14, y });
  y = drawPair(ops, { label: 'Joining Date', value: joiningDate || '-', x: PAGE_INNER_X + 14, y });
  y = drawPair(ops, { label: 'Payment Type', value: paymentType || 'Rent', x: PAGE_INNER_X + 14, y });
  y = drawPair(ops, { label: 'Rent Paid Date', value: paymentDate || '-', x: PAGE_INNER_X + 14, y });
  y = drawPair(ops, { label: 'Billing Month', value: billingMonthLabel || '-', x: PAGE_INNER_X + 14, y });
  y = drawPair(ops, { label: 'Amount Paid', value: amountPaid || '-', x: PAGE_INNER_X + 14, y });
  y -= 4;
  ops.push(`${PAGE_INNER_X + 14} ${y} m ${PAGE_INNER_X + PAGE_INNER_WIDTH - 14} ${y} l S`);
  y -= 14;

  drawWrappedText(ops, {
    text: 'This is a computer generated mail and does not require any signature.',
    x: PAGE_INNER_X + 14,
    y,
    maxChars: 90,
    lineGap: 14,
    font: 'F1',
    size: 10
  });

  const contentStream = `${ops.join('\n')}\n`;
  const obj1 = '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n';
  const obj2 = '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n';
  const obj3 =
    `3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${A4_WIDTH} ${A4_HEIGHT}] ` +
    '/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n';
  const obj4 = '4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n';
  const obj5 = '5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n';
  const obj6 =
    `6 0 obj\n<< /Length ${byteLength(contentStream)} >>\nstream\n` +
    `${contentStream}endstream\nendobj\n`;

  const header = '%PDF-1.4\n';
  const objects = [obj1, obj2, obj3, obj4, obj5, obj6];
  const offsets = [0];
  let assembled = header;
  objects.forEach((obj) => {
    offsets.push(byteLength(assembled));
    assembled += obj;
  });

  const xrefStart = byteLength(assembled);
  const xrefRows = ['xref', `0 ${objects.length + 1}`, '0000000000 65535 f '];
  for (let i = 1; i <= objects.length; i += 1) {
    xrefRows.push(`${formatOffset(offsets[i])} 00000 n `);
  }
  const trailer = [
    ...xrefRows,
    'trailer',
    `<< /Size ${objects.length + 1} /Root 1 0 R >>`,
    'startxref',
    `${xrefStart}`,
    '%%EOF'
  ].join('\n');

  return new Blob([`${assembled}${trailer}`], { type: 'application/pdf' });
};
