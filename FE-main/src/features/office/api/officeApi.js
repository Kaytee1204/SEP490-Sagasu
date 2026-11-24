import api from "@/services/apiClient";
const data = async (promise) => (await promise).data;
const query = (values) =>
  new URLSearchParams(
    Object.entries(values).filter(([, v]) => v !== "" && v != null),
  );
export const officeApi = {
  questionnaire: (postId,counter) => data(api.get(`/office-claims/questionnaire?${query({postId,counter})}`)),
  claimants: (q) => data(api.get(`/office-claims/claimants?${query({ q })}`)),
  posts: (filters) => data(api.get(`/office/posts?${query(filters)}`)),
  post: (id) => data(api.get(`/office/posts/${id}`)),
  moderate: (id, body) =>
    data(api.post(`/office/posts/${id}/moderation`, body)),
  stored: (filters) => data(api.get(`/office/stored-items?${query(filters)}`)),
  locations: () => data(api.get("/office/locations")),
  saveLocation: (id, body) =>
    data(
      id
        ? api.put(`/office/locations/${id}`, body)
        : api.post("/office/locations", body),
    ),
  claims: (filters, mine = false) =>
    data(api.get(`/office-claims${mine ? "/mine" : ""}?${query(filters)}`)),
  claimGroups: (filters) => data(api.get(`/office-claims/groups?${query(filters)}`)),
  claimsForPost: (postId, filters) => data(api.get(`/office-claims/posts/${postId}?${query(filters)}`)),
  claim: (id) => data(api.get(`/office-claims/${id}`)),
  personalClaim: (id) => data(api.get(`/office-claims/mine/${id}`)),
  review: (id, body) => data(api.post(`/office-claims/${id}/review`, body)),
  supplements: (id) => data(api.get(`/office-claims/${id}/supplements`)),
  resend: (id) => data(api.post(`/office-claims/${id}/appointment-email`, {})),
  submit: (body, files, counter) => {
    const form = new FormData();
    form.append(
      "data",
      new Blob([JSON.stringify(body)], { type: "application/json" }),
    );
    files.forEach((file) => form.append("images", file));
    return data(api.upload(`/office-claims${counter ? "/counter" : ""}`, form));
  },
};
