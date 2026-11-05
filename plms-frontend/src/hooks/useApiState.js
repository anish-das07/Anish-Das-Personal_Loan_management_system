import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Loads data with explicit loading / error / empty / success states and a reload function.
 * Ignores results that arrive after the component unmounted or after a newer request.
 */
export function useApiState(loader, deps = []) {
  const [state, setState] = useState({ loading: true, error: null, data: null });
  const requestId = useRef(0);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps);

  const reload = useCallback(async () => {
    const id = ++requestId.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await load();
      if (id === requestId.current) setState({ loading: false, error: null, data });
    } catch (error) {
      if (id === requestId.current) setState({ loading: false, error, data: null });
    }
  }, [load]);

  useEffect(() => {
    reload();
    return () => {
      requestId.current++;
    };
  }, [reload]);

  return { ...state, reload };
}
