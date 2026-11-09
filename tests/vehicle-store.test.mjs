import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createVehicleStore,
  VEHICLE_STORAGE_KEY,
} from "../src/data/vehicle-store.ts";

const carA = {
  id: "a",
  name: "Citroën C4",
  bluetoothName: "CITROEN",
  bluetoothAddress: "AA:01",
  nfcTagId: "TAG-A",
  isPrimary: true,
};
const carB = {
  id: "b",
  name: "Weekend car",
  bluetoothName: "CAR B",
  bluetoothAddress: "BB:02",
  nfcTagId: "TAG-B",
};

function fixture(
  state,
  selected = { name: carA.name, address: carA.bluetoothAddress },
  tag = "TAG-A",
) {
  let raw = state === undefined ? null : JSON.stringify(state);
  let nativeSelected = selected;
  let nativeTag = tag;
  const calls = [];
  let failActivation = false;
  const storage = {
    async getItem(key) {
      assert.equal(key, VEHICLE_STORAGE_KEY);
      return raw;
    },
    async setItem(key, value) {
      assert.equal(key, VEHICLE_STORAGE_KEY);
      raw = value;
    },
  };
  const native = {
    async getSelectedCar() {
      return nativeSelected;
    },
    async getNfcTagId() {
      return nativeTag;
    },
    async activateVehicle(name, address, nfcTagId) {
      calls.push({ name, address, nfcTagId });
      if (failActivation) throw new Error("Native unavailable");
      nativeSelected = { name, address };
      nativeTag = nfcTagId;
    },
  };
  return {
    store: createVehicleStore(storage, native),
    calls,
    persisted: () => JSON.parse(raw),
    setNativeTag(value) {
      nativeTag = value;
    },
    fail(value) {
      failActivation = value;
    },
  };
}

test("migrates native selected car and tag once without reselecting or resetting native state", async () => {
  const f = fixture(undefined, { name: "CITROEN", address: "AA:01" });
  const state = await f.store.refresh();
  assert.equal(state.vehicles.length, 1);
  assert.equal(state.activeVehicleId, state.vehicles[0].id);
  assert.equal(state.vehicles[0].nfcTagId, "TAG-A");
  assert.equal(state.vehicles[0].isPrimary, true);
  await f.store.refresh();
  assert.equal(f.calls.length, 0);
});

test("fresh install has a safe empty state; first added vehicle becomes primary and active", async () => {
  const f = fixture(undefined, { name: null, address: null }, null);
  assert.deepEqual(await f.store.refresh(), {
    vehicles: [],
    activeVehicleId: null,
  });
  const state = await f.store.save(carA);
  assert.equal(state.activeVehicleId, "a");
  assert.equal(state.vehicles[0].isPrimary, true);
  assert.equal(state.vehicles[0].nfcTagId, undefined); // input cannot fabricate enrollment
  assert.deepEqual(f.calls, [
    { name: carA.name, address: "AA:01", nfcTagId: null },
  ]);
});

test("adding an additional vehicle does not disturb the active vehicle or its tag", async () => {
  const f = fixture({ vehicles: [carA], activeVehicleId: "a" });
  const state = await f.store.save(carB);
  assert.equal(state.activeVehicleId, "a");
  assert.equal(state.vehicles[0].nfcTagId, "TAG-A");
  assert.equal(state.vehicles[1].nfcTagId, undefined);
  assert.equal(f.calls.length, 0);
});

test("switches one native vehicle and restores only that vehicle's NFC tag", async () => {
  const f = fixture({ vehicles: [carA, carB], activeVehicleId: "a" });
  await f.store.activate("b");
  await f.store.activate("a");
  assert.deepEqual(f.calls, [
    { name: carB.name, address: "BB:02", nfcTagId: "TAG-B" },
    { name: carA.name, address: "AA:01", nfcTagId: "TAG-A" },
  ]);
});

test("switching to a vehicle without a tag explicitly clears native tag", async () => {
  const f = fixture({
    vehicles: [carA, { ...carB, nfcTagId: undefined }],
    activeVehicleId: "a",
  });
  await f.store.activate("b");
  assert.equal(f.calls[0].nfcTagId, null);
  assert.equal((await f.store.refresh()).vehicles[0].nfcTagId, "TAG-A");
});

test("reselecting active vehicle and repeated refreshes do not call native activation", async () => {
  const f = fixture({ vehicles: [carA], activeVehicleId: "a" });
  await f.store.activate("a");
  await f.store.refresh();
  assert.equal(f.calls.length, 0);
});

test("captures enrollment completed while JS was paused, only into the active vehicle", async () => {
  const f = fixture({ vehicles: [carA, carB], activeVehicleId: "a" });
  f.setNativeTag("NEW-TAG-A");
  const state = await f.store.refresh();
  assert.equal(state.vehicles[0].nfcTagId, "NEW-TAG-A");
  assert.equal(state.vehicles[1].nfcTagId, "TAG-B");
  assert.equal(f.calls.length, 0);
});

test("friendly-name edit preserves tag; changing Bluetooth device clears it", async () => {
  const f = fixture({ vehicles: [carA], activeVehicleId: "a" });
  const renamed = await f.store.save({ ...carA, name: "  Daily driver  " });
  assert.equal(renamed.vehicles[0].name, "Daily driver");
  assert.equal(f.calls[0].nfcTagId, "TAG-A");
  const changed = await f.store.save({
    ...renamed.vehicles[0],
    bluetoothAddress: "CC:03",
  });
  assert.equal(changed.vehicles[0].nfcTagId, undefined);
  assert.equal(f.calls[1].nfcTagId, null);
});

test("prevents duplicate Bluetooth devices regardless of address case", async () => {
  const f = fixture({ vehicles: [carA], activeVehicleId: "a" });
  await assert.rejects(
    f.store.save({ ...carB, bluetoothAddress: "aa:01" }),
    /already belongs/,
  );
  assert.equal(f.persisted().vehicles.length, 1);
});

test("deleting active vehicle selects a safe replacement and its tag", async () => {
  const f = fixture({ vehicles: [carA, carB], activeVehicleId: "a" });
  const state = await f.store.remove("a");
  assert.equal(state.activeVehicleId, "b");
  assert.equal(state.vehicles[0].isPrimary, true);
  assert.deepEqual(f.calls, [
    { name: carB.name, address: "BB:02", nfcTagId: "TAG-B" },
  ]);
  await assert.rejects(f.store.remove("b"), /Keep at least one/);
});

test("removing an inactive vehicle or its tag does not change native selection", async () => {
  const f = fixture({ vehicles: [carA, carB], activeVehicleId: "a" });
  const state = await f.store.removeTag("b");
  assert.equal(state.vehicles[1].nfcTagId, undefined);
  await f.store.remove("b");
  assert.equal(f.calls.length, 0);
});

test("removing the active tag updates native state and cannot be resurrected by refresh", async () => {
  const f = fixture({ vehicles: [carA], activeVehicleId: "a" });
  await f.store.removeTag("a");
  assert.deepEqual(f.calls, [
    { name: carA.name, address: "AA:01", nfcTagId: null },
  ]);
  assert.equal((await f.store.refresh()).vehicles[0].nfcTagId, undefined);
});

test("failed native activation rolls storage back and queue remains usable", async () => {
  const f = fixture({ vehicles: [carA, carB], activeVehicleId: "a" });
  f.fail(true);
  await assert.rejects(f.store.activate("b"), /Native unavailable/);
  assert.equal(f.persisted().activeVehicleId, "a");
  f.fail(false);
  assert.equal((await f.store.activate("b")).activeVehicleId, "b");
});

test("recovers interrupted activation and does not import previous vehicle's native tag", async () => {
  const f = fixture({
    vehicles: [carA, carB],
    activeVehicleId: "b",
    nativeSyncPending: true,
  });
  const state = await f.store.refresh();
  assert.equal(state.vehicles[1].nfcTagId, "TAG-B");
  assert.equal(f.calls[0].nfcTagId, "TAG-B");
  assert.equal(f.persisted().nativeSyncPending, undefined);
});

test("recovers an interrupted same-address tag removal before importing native tag", async () => {
  const f = fixture({
    vehicles: [{ ...carA, nfcTagId: undefined }],
    activeVehicleId: "a",
    nativeSyncPending: true,
  });
  const state = await f.store.refresh();
  assert.equal(f.calls[0].nfcTagId, null);
  assert.equal(state.vehicles[0].nfcTagId, undefined);
});

test("serializes overlapping switches in order", async () => {
  const f = fixture({ vehicles: [carA, carB], activeVehicleId: "a" });
  await Promise.all([f.store.activate("b"), f.store.activate("a")]);
  assert.equal(f.persisted().activeVehicleId, "a");
  assert.deepEqual(
    f.calls.map((call) => call.nfcTagId),
    ["TAG-B", "TAG-A"],
  );
});
