import AsyncStorage from "@react-native-async-storage/async-storage";
import { useEffect, useState } from "react";

export const GUIDE_SEEN_KEY = "@locksora/guide-seen:v1";

export function useLocksoraGuide() {
  const [visible, setVisible] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    let mounted = true;
    AsyncStorage.getItem(GUIDE_SEEN_KEY)
      .then((seen) => {
        if (mounted && seen !== "true") setVisible(true);
      })
      .catch(() => {
        if (mounted) setVisible(true);
      });
    return () => {
      mounted = false;
    };
  }, []);
  return {
    visible,
    error,
    open: () => {
      setError("");
      setVisible(true);
    },
    close: async () => {
      try {
        await AsyncStorage.setItem(GUIDE_SEEN_KEY, "true");
        setError("");
        setVisible(false);
      } catch {
        setError("Could not save your guide preference. Please try again.");
      }
    },
  };
}
