import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createVehicleStore,
  parseFleet,
  newerFleet,
  LEGACY_VEHICLE_STORAGE_KEY,
} from "../src/data/vehicle-store.ts";
const state = {
  schemaVersion: 2,
  revision: 1,
  legacyProfilesImported: true,
  vehicles: [],
  configuredNfcTags: ["TAG"],
  currentVehicleId: null,
};

test("an older RPC snapshot cannot overwrite a newer native event", () => {
  const newer = { ...state, revision: 8, currentVehicleId: "b" };
  assert.equal(newerFleet(newer, state), newer);
  assert.equal(newerFleet(state, newer), newer);
});
function fixture(imported = true) {
  const calls = [];
  let snapshot = { ...state, legacyProfilesImported: imported };
  let failing = false;
  const reply = () => JSON.stringify(snapshot);
  const native = {
    async getFleetState() {
      calls.push(["read"]);
      return reply();
    },
    async importLegacyProfiles(raw) {
      calls.push(["import", raw]);
      snapshot = { ...snapshot, legacyProfilesImported: true };
      return reply();
    },
    async saveVehicle(...args) {
      calls.push(["save", ...args]);
      if (failing) throw new Error("native failure");
      return reply();
    },
    async deleteVehicle(id) {
      calls.push(["delete", id]);
      return reply();
    },
    async removeNfcTag(id) {
      calls.push(["removeTag", id]);
      return reply();
    },
    async confirmVehicle(...args) {
      calls.push(["confirm", ...args]);
      return reply();
    },
  };
  const old = JSON.stringify({
    activeVehicleId: "old",
    vehicles: [
      {
        id: "old",
        name: "Old car",
        bluetoothAddress: "AA",
        nfcTagId: "OLD_TAG",
      },
    ],
  });
  const storage = {
    async getItem(key) {
      assert.equal(key, LEGACY_VEHICLE_STORAGE_KEY);
      calls.push(["legacyRead"]);
      return old;
    },
  };
  return {
    store: createVehicleStore(storage, native),
    calls,
    old,
    fail(value) {
      failing = value;
    },
  };
}
test("passes legacy profiles and NFC UIDs intact once; native import owns state decisions", async () => {
  const f = fixture(false);
  await f.store.refresh();
  await f.store.refresh();
  assert.deepEqual(
    f.calls.filter((call) => call[0] === "import"),
    [["import", f.old]],
  );
  assert.equal(f.calls.filter((call) => call[0] === "legacyRead").length, 1);
});
test("native snapshots are returned without any JS live-state persistence", async () => {
  const f = fixture();
  assert.deepEqual(await f.store.refresh(), state);
  assert.deepEqual(f.calls, [["read"]]);
});
test("vehicle edit sends metadata only, never tags or lock/connection state", async () => {
  const f = fixture();
  await f.store.save({
    id: "b",
    name: "  Octavia  ",
    bluetoothAddress: " BB:02 ",
    bluetoothName: "SKODA",
    locked: false,
    connected: true,
    nfcTagId: "WRONG",
  });
  assert.deepEqual(f.calls.at(-1), ["save", "b", "Octavia", "BB:02", "SKODA"]);
});
test("manual confirmation sends originating vehicle and displayed disconnect generation", async () => {
  const f = fixture();
  await f.store.confirm("a", 123);
  assert.deepEqual(f.calls.at(-1), ["confirm", "a", 123]);
});
test("vehicle deletion and global tag removal are separate native commands", async () => {
  const f = fixture();
  await f.store.remove("a");
  await f.store.removeTag("TAG");
  assert.deepEqual(
    f.calls.filter((call) => call[0] !== "read"),
    [
      ["delete", "a"],
      ["removeTag", "TAG"],
    ],
  );
});
test("native errors propagate without poisoning the command queue", async () => {
  const f = fixture();
  f.fail(true);
  await assert.rejects(
    f.store.save({ id: "a", name: "Car", bluetoothAddress: "AA" }),
    /native failure/,
  );
  f.fail(false);
  assert.deepEqual(await f.store.refresh(), state);
});
test("invalid schema is rejected; no manual activation command exists", () => {
  assert.throws(
    () => parseFleet('{"schemaVersion":99}'),
    /could not be loaded/,
  );
  assert.equal("activate" in fixture().store, false);
});
