import { useCallback, useEffect, useState } from "react";
import { reviewApi } from "../api";

// Associate responses with their request so an older filter cannot replace a newer result.
export default function useReviewResource(type, params, revision = 0) {
  const key = JSON.stringify([type, params, revision]);
  const [state, setState] = useState(null);
  useEffect(() => {
    let active = true;
    const [kind, args] = JSON.parse(key);
    const request =
      kind === "queue"
        ? reviewApi.list(args)
        : kind === "detail"
          ? reviewApi.detail(args)
          : Promise.all([reviewApi.mine(), reviewApi.results()]);
    request
      .then((data) => {
        if (active) setState({ key, data, error: "" });
      })
      .catch((error) => {
        if (active) setState({ key, data: null, error: error.message });
      });
    return () => {
      active = false;
    };
  }, [key]);
  const setData = useCallback(
    (data) => setState({ key, data, error: "" }),
    [key],
  );
  const setError = useCallback(
    (error) =>
      setState((old) => ({
        key,
        data: old?.key === key ? old.data : null,
        error,
      })),
    [key],
  );
  return {
    data: state?.key === key ? state.data : null,
    error: state?.key === key ? state.error : "",
    setData,
    setError,
  };
}
