// Invitation tokens are sent in the request body, never in API URLs or auth headers.
const base = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
async function request(action, body, intake = false) {
  const response = await fetch(`${base}/${intake ? "office-intake-invitations" : "office-invitations"}/${action}`, {
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
export const intakeInvitationApi = {
  preview: (token) => request("preview", { token }, true),
  respond: (token, decision) => request("respond", { token, decision }, true),
};
