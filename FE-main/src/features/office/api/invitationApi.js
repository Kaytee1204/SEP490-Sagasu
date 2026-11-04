// Invitation tokens are sent in the request body, never in API URLs or auth headers.
const base = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
async function request(action, body) {
  const response = await fetch(`${base}/office-invitations/${action}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "omit",
    cache: "no-store",
    referrerPolicy: "no-referrer",
    body: JSON.stringify(body),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(
      result.message || "Chưa thể xử lý lời mời. Vui lòng thử lại.",
    );
  return result.data;
}
export const invitationApi = {
  preview: (token) => request("preview", { token }),
  respond: (token, decision) => request("respond", { token, decision }),
};
