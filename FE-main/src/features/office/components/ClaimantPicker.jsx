import { useEffect, useState } from "react";
import { officeApi } from "../api/officeApi";
import useLoad from "../hooks/useLoad";
import LoadState from "./LoadState";

export default function ClaimantPicker({
  value,
  query,
  onQueryChange,
  onChange,
  disabled,
}) {
  const [keyword, setKeyword] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(query.trim()), 300);
    return () => clearTimeout(timer);
  }, [query]);
  const state = useLoad(
    () =>
      keyword.length >= 3 ? officeApi.claimants(keyword) : Promise.resolve([]),
    keyword,
  );
  return (
    <div className="of-form">
      <label>
        Địa chỉ thư điện tử *
        <input
          type="email"
          required
          autoComplete="off"
          value={query}
          maxLength={255}
          disabled={disabled}
          placeholder="Nhập ít nhất 3 ký tự trong email…"
          onChange={(event) => {
            onQueryChange(event.target.value);
          }}
        />
        <small>
          Chọn đúng họ tên và email bên dưới. Tối đa 10 kết quả; nhập thêm email
          để thu hẹp tìm kiếm.
        </small>
      </label>
      {!value &&
        query.trim().length >= 3 &&
        (query.trim() !== keyword ? (
          <p role="status">Đang tìm tài khoản…</p>
        ) : (
          <LoadState state={state}>
            <div className="of-list-choice" aria-label="Kết quả tìm tài khoản">
              {state.data?.map((account) => (
                <button
                  type="button"
                  className="of-button"
                  disabled={disabled}
                  key={account.id}
                  onClick={() => onChange(account)}
                >
                  {account.name} · {account.email}
                </button>
              ))}
              {state.data?.length === 0 && (
                <p role="status">
                  Chưa tìm thấy tài khoản đang hoạt động với email này. Kiểm tra
                  lại email hoặc đề nghị người nhận đăng ký tài khoản trước.
                </p>
              )}
            </div>
          </LoadState>
        ))}
    </div>
  );
}
