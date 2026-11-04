import { useState } from "react";
import { officeApi } from "../api/officeApi";
import Notice from "./Notice";

export default function SupplementForm({ claim, onDone }) {
  const [information, setInformation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await officeApi.supplement(claim.id, {
        information: information.trim(),
        expectedVersion: claim.version,
      });
      onDone();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="of-panel">
      <h2>Bổ sung thông tin</h2>
      <form className="of-form" onSubmit={submit}>
        <Notice error>{error}</Notice>
        <label>
          Nội dung người khai cung cấp *
          <textarea
            required
            maxLength={2000}
            value={information}
            disabled={busy}
            onChange={(e) => setInformation(e.target.value)}
          />
        </label>
        <button
          className="of-button of-primary"
          disabled={busy || !information.trim()}
        >
          {busy ? "Đang gửi…" : "Ghi nhận & gửi lại xét duyệt"}
        </button>
      </form>
    </section>
  );
}
