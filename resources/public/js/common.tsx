import { useRef, useEffect } from "react";

export const getCSRFToken = ():string => {
    const elem : HTMLInputElement = document.getElementById("__anti-forgery-token") as unknown as HTMLInputElement
    const token : string = elem.value as unknown as string;
    return token
}

export function useInterval(callback: () => void, delay: number) {
  const savedCallback = useRef(callback);

  // Remember the latest callback.
  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  // Set up the interval.
  useEffect(() => {
    function tick() {
      savedCallback.current();
    }
    if (delay !== null) {
      let id = setInterval(tick, delay);
      return () => clearInterval(id);
    }
  }, [delay]);
}
