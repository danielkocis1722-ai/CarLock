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
  type Vehicle,
  type VehicleState,
} from "../data/vehicle-store";

const store = createVehicleStore(AsyncStorage, CarlockBluetoothModule);
type EnrollmentFeedback = { success: boolean; vehicleName: string } | null;
type VehiclesContextValue = VehicleState & {
  activeVehicle: Vehicle | undefined;
  ready: boolean;
  busy: boolean;
  error: string;
  enrollmentFeedback: EnrollmentFeedback;
  clearEnrollmentFeedback(): void;
  refresh(): Promise<VehicleState>;
  save(vehicle: Vehicle): Promise<VehicleState>;
  activate(id: string): Promise<VehicleState>;
  remove(id: string): Promise<VehicleState>;
  removeTag(id: string): Promise<VehicleState>;
};
const VehiclesContext = createContext<VehiclesContextValue | null>(null);

export function VehiclesProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<VehicleState>({
    vehicles: [],
    activeVehicleId: null,
  });
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [enrollmentFeedback, setEnrollmentFeedback] =
    useState<EnrollmentFeedback>(null);
  const pending = useRef(0);

  const run = useCallback(async (operation: () => Promise<VehicleState>) => {
    pending.current += 1;
    setBusy(true);
    try {
      const next = await operation();
      setState(next);
      setError("");
      return next;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save vehicles.");
      throw err;
    } finally {
      pending.current -= 1;
      setBusy(pending.current > 0);
      setReady(true);
    }
  }, []);
  const refresh = useCallback(() => run(store.refresh), [run]);

  useEffect(() => {
    refresh().catch(() => undefined);
    const appState = AppState.addEventListener("change", (next) => {
      if (next === "active") refresh().catch(() => undefined);
    });
    const enrollment = CarlockBluetoothModule.addListener(
      "onNfcEnrollmentFinished",
      (event) => {
        refresh()
          .then((next) => {
            const vehicle = next.vehicles.find(
              (item) =>
                item.bluetoothAddress?.toUpperCase() ===
                event.address?.toUpperCase(),
            );
            setEnrollmentFeedback({
              success: event.success,
              vehicleName: vehicle?.name ?? "your vehicle",
            });
          })
          .catch(() => undefined);
      },
    );
    return () => {
      appState.remove();
      enrollment.remove();
    };
  }, [refresh]);

  return (
    <VehiclesContext.Provider
      value={{
        ...state,
        activeVehicle: state.vehicles.find(
          (vehicle) => vehicle.id === state.activeVehicleId,
        ),
        ready,
        busy,
        error,
        enrollmentFeedback,
        clearEnrollmentFeedback: () => setEnrollmentFeedback(null),
        refresh,
        save: (vehicle) => run(() => store.save(vehicle)),
        activate: (id) => run(() => store.activate(id)),
        remove: (id) => run(() => store.remove(id)),
        removeTag: (id) => run(() => store.removeTag(id)),
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
