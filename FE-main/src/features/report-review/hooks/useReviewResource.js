import { useCallback, useEffect, useState } from "react";
import { reviewApi } from "../api";

// Associate responses with their request so an older filter cannot replace a newer result.
export default function useReviewResource(type, params, revision = 0) {
  const key = JSON.stringify([type, params, revision]);
  const resourceKey = JSON.stringify([type, params]);
  const queueScope = type === "queue" ? JSON.stringify({ ...params, queue: undefined, page: undefined }) : null;
  const [state, setState] = useState(null);
  useEffect(() => {
    let active = true;
    const [kind, args] = JSON.parse(resourceKey);
    const request =
      kind === "queue"
        ? reviewApi.list(args)
        : kind === "detail"
          ? reviewApi.detail(args)
          : Promise.all([reviewApi.mine(), reviewApi.results()]);
    request
      .then((data) => {
        if (active) setState({ key, resourceKey, queueScope, data, error: "" });
      })
      .catch((error) => {
        if (active) setState(old => ({ key, resourceKey, queueScope, data: kind === "queue" && old?.resourceKey === resourceKey ? old.data : null, error: error.message }));
      });
    return () => {
      active = false;
    };
  }, [key, resourceKey, queueScope]);
  const setData = useCallback(
    (data) => setState({ key, resourceKey, queueScope, data, error: "" }),
    [key, resourceKey, queueScope],
  );
  const setError = useCallback(
    (error) =>
      setState((old) => ({
        key,
        resourceKey,
        queueScope,
        data: old?.key === key ? old.data : null,
        error,
      })),
    [key, resourceKey, queueScope],
  );
  return {
    data: state?.key === key || (type === "queue" && state?.resourceKey === resourceKey) ? state?.data : null,
    countsByQueue: state?.queueScope === queueScope ? state?.data?.countsByQueue : null,
    error: state?.key === key ? state.error : "",
    refreshing: state?.key !== key,
    setData,
    setError,
  };
}
