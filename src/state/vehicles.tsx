import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { AppState } from "react-native";
import CarlockBluetoothModule from "../../modules/carlock-bluetooth/src/CarlockBluetoothModule";
import {
  createVehicleStore,
  parseFleet,
  newerFleet,
  type Vehicle,
  type VehicleDraft,
  type VehicleState,
} from "../data/vehicle-store";

const store = createVehicleStore(AsyncStorage, CarlockBluetoothModule);
type VehiclesContextValue = VehicleState & {
  currentVehicle: Vehicle | undefined;
  ready: boolean;
  busy: boolean;
  error: string;
  enrollmentFeedback: { success: boolean } | null;
  clearEnrollmentFeedback(): void;
  refresh(): Promise<VehicleState>;
  save(vehicle: VehicleDraft): Promise<VehicleState>;
  remove(id: string): Promise<VehicleState>;
  removeTag(id: string): Promise<VehicleState>;
  confirm(id: string, disconnectAt: number): Promise<VehicleState>;
};
const VehiclesContext = createContext<VehiclesContextValue | null>(null);

export function VehiclesProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<VehicleState>({
    schemaVersion: 2,
    revision: -1,
    vehicles: [],
    configuredNfcTags: [],
    currentVehicleId: null,
    legacyProfilesImported: false,
  });
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [enrollmentFeedback, setEnrollmentFeedback] = useState<{
    success: boolean;
  } | null>(null);
  const pending = useRef(0);
  const accept = useCallback((next: VehicleState) => {
    // A queued RPC must not overwrite a newer Bluetooth/NFC event snapshot.
    setState((current) => newerFleet(current, next));
  }, []);
  const run = useCallback(
    async (operation: () => Promise<VehicleState>) => {
      pending.current += 1;
      setBusy(true);
      try {
        const next = await operation();
        accept(next);
        setError("");
        return next;
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Could not update vehicles.",
        );
        throw err;
      } finally {
        pending.current -= 1;
        setBusy(pending.current > 0);
        setReady(true);
      }
    },
    [accept],
  );
  const refresh = useCallback(() => run(store.refresh), [run]);

  useEffect(() => {
    const fleet = CarlockBluetoothModule.addListener(
      "onFleetChanged",
      (event) => {
        try {
          accept(parseFleet(event.stateJson));
        } catch {
          setError(
            "Could not read the vehicle update. Please reopen Locksora.",
          );
        }
      },
    );
    refresh().catch(() => undefined);
    const appState = AppState.addEventListener("change", (next) => {
      if (next === "active") refresh().catch(() => undefined);
    });
    const enrollment = CarlockBluetoothModule.addListener(
      "onNfcEnrollmentFinished",
      (event) => {
        refresh()
          .then(() => setEnrollmentFeedback({ success: event.success }))
          .catch(() => undefined);
      },
    );
    return () => {
      fleet.remove();
      appState.remove();
      enrollment.remove();
    };
  }, [refresh, accept]);

  return (
    <VehiclesContext.Provider
      value={{
        ...state,
        currentVehicle: state.vehicles.find(
          (vehicle) => vehicle.id === state.currentVehicleId,
        ),
        ready,
        busy,
        error,
        enrollmentFeedback,
        clearEnrollmentFeedback: () => setEnrollmentFeedback(null),
        refresh,
        save: (vehicle) => run(() => store.save(vehicle)),
        remove: (id) => run(() => store.remove(id)),
        removeTag: (id) => run(() => store.removeTag(id)),
        confirm: (id, disconnectAt) =>
          run(() => store.confirm(id, disconnectAt)),
      }}
    >
      {children}
    </VehiclesContext.Provider>
  );
}

export function useVehicles() {
  const value = useContext(VehiclesContext);
  if (!value)
    throw new Error("useVehicles must be used inside VehiclesProvider");
  return value;
}
