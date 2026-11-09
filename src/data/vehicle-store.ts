export type Vehicle = {
  id: string;
  name: string;
  bluetoothName?: string;
  bluetoothAddress?: string;
  nfcTagId?: string;
  isPrimary?: boolean;
};

export type VehicleState = {
  vehicles: Vehicle[];
  activeVehicleId: string | null;
};

type StoredVehicleState = VehicleState & { nativeSyncPending?: boolean };

export const VEHICLE_STORAGE_KEY = "@locksora/vehicles:v1";

type Storage = {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
};

type NativeVehicles = {
  getSelectedCar(): Promise<{ name: string | null; address: string | null }>;
  getNfcTagId(): Promise<string | null>;
  activateVehicle(
    name: string,
    address: string,
    nfcTagId: string | null,
  ): Promise<void>;
};

const addressKey = (address?: string | null) =>
  address?.trim().toUpperCase() ?? "";
const activeVehicle = (state: VehicleState) =>
  state.vehicles.find((vehicle) => vehicle.id === state.activeVehicleId);

/** Serialized profile changes: native state always represents exactly one active vehicle. */
export function createVehicleStore(storage: Storage, native: NativeVehicles) {
  let queue: Promise<unknown> = Promise.resolve();

  function serialize<T>(operation: () => Promise<T>): Promise<T> {
    const result = queue.then(operation);
    queue = result.catch(() => undefined);
    return result;
  }

  const persist = (state: StoredVehicleState) =>
    storage.setItem(VEHICLE_STORAGE_KEY, JSON.stringify(state));

  async function load(): Promise<StoredVehicleState> {
    const raw = await storage.getItem(VEHICLE_STORAGE_KEY);
    if (raw !== null) {
      const state = JSON.parse(raw) as StoredVehicleState;
      // Do not silently overwrite unreadable profiles or reactivate the wrong car.
      if (
        !Array.isArray(state.vehicles) ||
        state.vehicles.some((vehicle) => !vehicle.id || !vehicle.name) ||
        (state.vehicles.length > 0 && !activeVehicle(state))
      ) {
        throw new Error(
          "Saved vehicles could not be loaded. Please restart Locksora.",
        );
      }
      return state;
    }
    const selected = await native.getSelectedCar();
    const nfcTagId = await native.getNfcTagId();
    const first: Vehicle | null =
      selected.address || selected.name
        ? {
            id: "migrated-vehicle",
            name: selected.name || "My vehicle",
            bluetoothName: selected.name || undefined,
            bluetoothAddress: selected.address || undefined,
            nfcTagId: nfcTagId || undefined,
            isPrimary: true,
          }
        : null;
    const state = {
      vehicles: first ? [first] : [],
      activeVehicleId: first?.id ?? null,
    };
    await persist(state);
    return state;
  }

  async function refresh(): Promise<VehicleState> {
    const state = await load();
    const active = activeVehicle(state);
    if (!active) return state;
    const selected = await native.getSelectedCar();
    if (
      state.nativeSyncPending ||
      addressKey(active.bluetoothAddress) !== addressKey(selected.address)
    ) {
      if (active.bluetoothAddress) {
        // Recover a profile switch persisted before a process interruption.
        await native.activateVehicle(
          active.name,
          active.bluetoothAddress,
          active.nfcTagId ?? null,
        );
      }
      const { nativeSyncPending: _, ...recovered } = state;
      await persist(recovered);
      return recovered;
    }
    // Native enrollment can complete while JS is paused or not running.
    const tagId = (await native.getNfcTagId()) || undefined;
    if (active.nfcTagId === tagId) return state;
    const next = {
      ...state,
      vehicles: state.vehicles.map((vehicle) =>
        vehicle.id === active.id ? { ...vehicle, nfcTagId: tagId } : vehicle,
      ),
    };
    await persist(next);
    return next;
  }

  async function commit(
    previous: VehicleState,
    next: VehicleState,
  ): Promise<VehicleState> {
    const before = activeVehicle(previous);
    const after = activeVehicle(next);
    const needsSync =
      after?.bluetoothAddress &&
      (before?.id !== after.id ||
        before?.name !== after.name ||
        before?.bluetoothAddress !== after.bluetoothAddress ||
        before?.nfcTagId !== after.nfcTagId);
    // The marker also protects same-address edits/removals from being overwritten on restart.
    await persist(needsSync ? { ...next, nativeSyncPending: true } : next);
    if (after?.bluetoothAddress && needsSync) {
      try {
        await native.activateVehicle(
          after.name,
          after.bluetoothAddress,
          after.nfcTagId ?? null,
        );
      } catch (error) {
        await persist(previous);
        throw error;
      }
      await persist(next);
    }
    return next;
  }

  return {
    refresh: () => serialize(refresh),
    save: (input: Vehicle) =>
      serialize(async () => {
        const state = await refresh();
        const name = input.name.trim();
        const address = input.bluetoothAddress?.trim();
        if (!name || !address)
          throw new Error(
            "Enter a vehicle name and choose its Bluetooth device.",
          );
        if (
          state.vehicles.some(
            (vehicle) =>
              vehicle.id !== input.id &&
              addressKey(vehicle.bluetoothAddress) === addressKey(address),
          )
        ) {
          throw new Error(
            "That Bluetooth device already belongs to another vehicle.",
          );
        }
        const previous = state.vehicles.find(
          (vehicle) => vehicle.id === input.id,
        );
        const vehicle: Vehicle = {
          ...input,
          name,
          bluetoothAddress: address,
          // Editing cannot copy tags between devices or invent a configured tag.
          nfcTagId:
            previous &&
            addressKey(previous.bluetoothAddress) === addressKey(address)
              ? previous.nfcTagId
              : undefined,
          isPrimary: previous?.isPrimary ?? state.vehicles.length === 0,
        };
        return commit(state, {
          vehicles: previous
            ? state.vehicles.map((item) =>
                item.id === vehicle.id ? vehicle : item,
              )
            : [...state.vehicles, vehicle],
          activeVehicleId: state.activeVehicleId ?? vehicle.id,
        });
      }),
    activate: (id: string) =>
      serialize(async () => {
        const state = await refresh();
        const vehicle = state.vehicles.find((item) => item.id === id);
        if (!vehicle?.bluetoothAddress)
          throw new Error("Choose this vehicle's Bluetooth device first.");
        return commit(state, { ...state, activeVehicleId: id });
      }),
    remove: (id: string) =>
      serialize(async () => {
        const state = await refresh();
        if (state.vehicles.length <= 1)
          throw new Error(
            "Keep at least one vehicle. Add another before deleting this one.",
          );
        const remaining = state.vehicles.filter((vehicle) => vehicle.id !== id);
        const nextActive =
          state.activeVehicleId === id
            ? remaining.find((vehicle) => vehicle.bluetoothAddress)?.id
            : state.activeVehicleId;
        if (!nextActive)
          throw new Error(
            "Configure another vehicle's Bluetooth device before deleting this one.",
          );
        if (!remaining.some((vehicle) => vehicle.isPrimary))
          remaining[0] = { ...remaining[0], isPrimary: true };
        return commit(state, {
          vehicles: remaining,
          activeVehicleId: nextActive,
        });
      }),
    removeTag: (id: string) =>
      serialize(async () => {
        const state = await refresh();
        return commit(state, {
          ...state,
          vehicles: state.vehicles.map((vehicle) =>
            vehicle.id === id ? { ...vehicle, nfcTagId: undefined } : vehicle,
          ),
        });
      }),
  };
}
