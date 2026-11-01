import { receiptState } from '../receiptUtils';

export default function ReceiptStatusBadge({ receipt }) {
  const state = receiptState(receipt);
  return <span className={`inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-sm font-semibold ring-1 ring-inset ${state.tone}`}>
    <span className={`h-1.5 w-1.5 rounded-full ${state.dot}`} />{state.label}
  </span>;
}
