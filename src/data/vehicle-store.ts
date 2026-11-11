export type Vehicle = {
  id: string;
  name: string;
  bluetoothName: string | null;
  bluetoothAddress: string;
  locked: boolean;
  connected: boolean;
  lastEvent: string;
  lastEventAt: number;
  lastDisconnectAt: number;
  pending: boolean;
  state: "LOCKED" | "IN_CAR" | "UNCONFIRMED";
};
export type VehicleDraft = Pick<Vehicle, "id" | "name" | "bluetoothAddress"> & {
  bluetoothName?: string | null;
};
export type VehicleState = {
  schemaVersion: 2;
  revision: number;
  vehicles: Vehicle[];
  configuredNfcTags: string[];
  currentVehicleId: string | null;
  legacyProfilesImported: boolean;
};
export const LEGACY_VEHICLE_STORAGE_KEY = "@locksora/vehicles:v1";
type Storage = { getItem(key: string): Promise<string | null> };
type NativeFleet = {
  getFleetState(): Promise<string>;
  importLegacyProfiles(raw: string | null): Promise<string>;
  saveVehicle(
    id: string,
    name: string,
    address: string,
    bluetoothName: string | null,
  ): Promise<string>;
  deleteVehicle(id: string): Promise<string>;
  removeNfcTag(id: string): Promise<string>;
  confirmVehicle(id: string, disconnectAt: number): Promise<string>;
};
export function parseFleet(raw: string): VehicleState {
  const fleet = JSON.parse(raw) as VehicleState;
  if (
    !fleet ||
    fleet.schemaVersion !== 2 ||
    !Array.isArray(fleet.vehicles) ||
    !Array.isArray(fleet.configuredNfcTags) ||
    typeof fleet.revision !== "number"
  ) {
    throw new Error("Locksora vehicle data could not be loaded.");
  }
  return fleet;
}

export const newerFleet = (
  current: VehicleState,
  next: VehicleState,
): VehicleState => (next.revision >= current.revision ? next : current);

/** JS only issues metadata commands. Native owns all states, tags, and event-derived context. */
export function createVehicleStore(storage: Storage, native: NativeFleet) {
  let queue: Promise<unknown> = Promise.resolve();
  function serialize(
    operation: () => Promise<VehicleState>,
  ): Promise<VehicleState> {
    const result = queue.then(operation);
    queue = result.catch(() => undefined);
    return result;
  }
  async function refresh() {
    const fleet = parseFleet(await native.getFleetState());
    if (fleet.legacyProfilesImported) return fleet;
    // Keep the old document as a backup. The native migration is atomic and idempotent.
    return parseFleet(
      await native.importLegacyProfiles(
        await storage.getItem(LEGACY_VEHICLE_STORAGE_KEY),
      ),
    );
  }
  return {
    refresh: () => serialize(refresh),
    save: (input: VehicleDraft) =>
      serialize(async () => {
        await refresh();
        if (!input.name.trim() || !input.bluetoothAddress.trim())
          throw new Error("Enter a name and choose a Bluetooth device.");
        return parseFleet(
          await native.saveVehicle(
            input.id,
            input.name.trim(),
            input.bluetoothAddress.trim(),
            input.bluetoothName ?? null,
          ),
        );
      }),
    remove: (id: string) =>
      serialize(async () => {
        await refresh();
        return parseFleet(await native.deleteVehicle(id));
      }),
    removeTag: (id: string) =>
      serialize(async () => {
        await refresh();
        return parseFleet(await native.removeNfcTag(id));
      }),
    confirm: (id: string, disconnectAt: number) =>
      serialize(async () => {
        await refresh();
        return parseFleet(await native.confirmVehicle(id, disconnectAt));
      }),
  };
}
