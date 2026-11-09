import Ionicons from "@expo/vector-icons/Ionicons";
import { useRouter } from "expo-router";
import { useState } from "react";
import {
  Alert,
  Modal,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import RNBluetoothClassic from "react-native-bluetooth-classic";
import CarlockBluetoothModule from "../../modules/carlock-bluetooth/src/CarlockBluetoothModule";
import { useVehicles } from "@/state/vehicles";
import type { Vehicle } from "@/data/vehicle-store";

type BluetoothDevice = { name?: string | null; address: string };
const message = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";

function Action({
  title,
  onPress,
  disabled,
  danger = false,
  primary = false,
}: {
  title: string;
  onPress(): void;
  disabled?: boolean;
  danger?: boolean;
  primary?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.action,
        primary && styles.primaryAction,
        (disabled || pressed) && { opacity: 0.5 },
      ]}
    >
      <Text style={[styles.actionText, danger && styles.danger]}>{title}</Text>
    </Pressable>
  );
}

export default function SettingsScreen() {
  const router = useRouter();
  const profiles = useVehicles();
  const [editor, setEditor] = useState<Vehicle | null>(null);
  const [devices, setDevices] = useState<BluetoothDevice[]>([]);
  const [loadingDevices, setLoadingDevices] = useState(false);
  const [error, setError] = useState("");
  const [editorError, setEditorError] = useState("");
  const [nfcTarget, setNfcTarget] = useState<Vehicle | null>(null);
  const [startingNfc, setStartingNfc] = useState(false);

  const loadDevices = async () => {
    setLoadingDevices(true);
    setEditorError("");
    try {
      if (Platform.OS !== "android")
        throw new Error("Vehicle Bluetooth setup is available on Android.");
      if (Number(Platform.Version) >= 31) {
        const grants = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
        ]);
        if (
          Object.values(grants).some(
            (grant) => grant !== PermissionsAndroid.RESULTS.GRANTED,
          )
        ) {
          throw new Error(
            "Allow Nearby devices permission in Android Settings to choose your vehicle.",
          );
        }
      }
      const bonded = await RNBluetoothClassic.getBondedDevices();
      setDevices(bonded);
    } catch (err) {
      setEditorError(message(err));
    } finally {
      setLoadingDevices(false);
    }
  };

  const openEditor = (vehicle?: Vehicle) => {
    setError("");
    setEditorError("");
    setDevices([]);
    setEditor(
      vehicle
        ? { ...vehicle }
        : {
            id: `vehicle-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`,
            name: "",
          },
    );
    loadDevices();
  };

  const saveVehicle = async () => {
    if (!editor) return;
    try {
      await profiles.save(editor);
      setEditor(null);
      setError("");
    } catch (err) {
      setEditorError(message(err));
    }
  };

  const activate = async (vehicle: Vehicle) => {
    setError("");
    try {
      await profiles.activate(vehicle.id);
    } catch (err) {
      setError(message(err));
    }
  };

  const deleteVehicle = (vehicle: Vehicle) => {
    const replacement = profiles.vehicles.find(
      (item) => item.id !== vehicle.id && item.bluetoothAddress,
    );
    Alert.alert(
      `Delete ${vehicle.name}?`,
      profiles.activeVehicleId === vehicle.id
        ? `${replacement?.name ?? "Another vehicle"} will become active. This vehicle's saved NFC tag will also be removed.`
        : "This removes the saved vehicle and its NFC tag from Locksora.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete vehicle",
          style: "destructive",
          onPress: async () => {
            try {
              await profiles.remove(vehicle.id);
              setError("");
            } catch (err) {
              setError(message(err));
            }
          },
        },
      ],
    );
  };

  const removeTag = (vehicle: Vehicle) =>
    Alert.alert(
      `Remove NFC tag for ${vehicle.name}?`,
      "You can configure another tag at any time. This does not change the lock state.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Remove tag",
          style: "destructive",
          onPress: async () => {
            try {
              await profiles.removeTag(vehicle.id);
              setError("");
            } catch (err) {
              setError(message(err));
            }
          },
        },
      ],
    );

  const startEnrollment = async () => {
    if (!nfcTarget) return;
    setStartingNfc(true);
    setError("");
    profiles.clearEnrollmentFeedback();
    try {
      if (nfcTarget.id !== profiles.activeVehicleId)
        await profiles.activate(nfcTarget.id);
      setNfcTarget(null);
      if (!(await CarlockBluetoothModule.configureNfcTag())) {
        throw new Error(
          "Enable NFC on a supported Android phone, then try again.",
        );
      }
    } catch (err) {
      setError(message(err));
    } finally {
      setStartingNfc(false);
    }
  };

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable
          style={styles.back}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Back"
        >
          <Ionicons name="chevron-back" size={24} color="#EAF4FF" />
        </Pressable>
        <Text style={styles.title} accessibilityRole="header">
          Settings
        </Text>
        <View style={{ width: 44 }} />
      </View>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <Text style={styles.eyebrow}>LOCKSORA · MY VEHICLES</Text>
        <Text style={styles.description}>
          Keep your vehicles together. Only the active vehicle is monitored.
        </Text>
        <Action
          title="Add vehicle"
          primary
          onPress={() => openEditor()}
          disabled={profiles.busy || !profiles.ready}
        />
        {error || profiles.error ? (
          <Text style={styles.error} accessibilityRole="alert">
            {error || profiles.error}
          </Text>
        ) : null}
        {profiles.enrollmentFeedback && (
          <View style={styles.notice} accessibilityRole="alert">
            <Text style={styles.vehicleName}>
              {profiles.enrollmentFeedback.success
                ? "NFC tag configured successfully."
                : "NFC setup cancelled."}
            </Text>
            <Text style={styles.description}>
              {profiles.enrollmentFeedback.success
                ? `Your tag belongs to ${profiles.enrollmentFeedback.vehicleName}. Enrollment did not change the lock state.`
                : "Your previous NFC tag has not been replaced."}
            </Text>
            <Action
              title="Dismiss"
              onPress={profiles.clearEnrollmentFeedback}
            />
          </View>
        )}
        {!profiles.ready ? (
          <Text style={styles.description}>Loading vehicles…</Text>
        ) : (
          profiles.vehicles.length === 0 && (
            <View style={styles.card}>
              <Ionicons name="car-sport-outline" size={38} color="#9FB7D9" />
              <Text style={styles.vehicleName}>Your first vehicle</Text>
              <Text style={styles.description}>
                Add a name and choose your car's paired Bluetooth device. Then
                configure an NFC tag to confirm locking.
              </Text>
            </View>
          )
        )}
        {profiles.vehicles.map((vehicle) => {
          const active = vehicle.id === profiles.activeVehicleId;
          return (
            <View
              key={vehicle.id}
              style={[styles.card, active && styles.activeCard]}
            >
              <View style={styles.vehicleHeader}>
                <View style={styles.carIcon}>
                  <Ionicons
                    name="car-sport"
                    size={26}
                    color={active ? "#5EDB8A" : "#9FB7D9"}
                  />
                </View>
                <Text style={[styles.vehicleName, { flex: 1 }]}>
                  {vehicle.name}
                </Text>
                {active && <Text style={styles.badge}>Active</Text>}
              </View>
              <View style={styles.detailRow}>
                <Ionicons name="bluetooth" size={18} color="#9FB7D9" />
                <Text style={styles.detail}>
                  {vehicle.bluetoothName ||
                    vehicle.bluetoothAddress ||
                    "No Bluetooth device configured"}
                </Text>
              </View>
              <View style={styles.detailRow}>
                <Ionicons
                  name="radio-outline"
                  size={18}
                  color={vehicle.nfcTagId ? "#5EDB8A" : "#9FB7D9"}
                />
                <Text
                  style={[
                    styles.detail,
                    vehicle.nfcTagId && { color: "#5EDB8A" },
                  ]}
                >
                  {vehicle.nfcTagId
                    ? "NFC tag configured"
                    : "No NFC tag configured"}
                </Text>
              </View>
              {!active && (
                <Action
                  title="Make active"
                  primary
                  disabled={profiles.busy || !vehicle.bluetoothAddress}
                  onPress={() => activate(vehicle)}
                />
              )}
              <View style={styles.actions}>
                <Action
                  title="Edit vehicle"
                  disabled={profiles.busy}
                  onPress={() => openEditor(vehicle)}
                />
                <Action
                  title="Delete vehicle"
                  danger
                  disabled={profiles.busy || profiles.vehicles.length <= 1}
                  onPress={() => deleteVehicle(vehicle)}
                />
              </View>
              <View style={styles.divider} />
              <Action
                title={
                  vehicle.nfcTagId ? "Reconfigure NFC tag" : "Configure NFC tag"
                }
                disabled={
                  profiles.busy ||
                  !vehicle.bluetoothAddress ||
                  Platform.OS !== "android"
                }
                onPress={() => {
                  setError("");
                  setNfcTarget(vehicle);
                }}
              />
              {vehicle.nfcTagId && (
                <Action
                  title="Remove NFC tag"
                  danger
                  disabled={profiles.busy}
                  onPress={() => removeTag(vehicle)}
                />
              )}
              {profiles.vehicles.length === 1 && (
                <Text style={styles.small}>
                  Keep at least one vehicle. Add another before deleting this
                  one.
                </Text>
              )}
            </View>
          );
        })}
        <Text style={styles.eyebrow}>NFC CONFIRMATION</Text>
        <Text style={styles.description}>
          Lock your vehicle first, then unlock your phone and scan its tag.
          Passive NFC confirmation generally requires an unlocked Android phone.
        </Text>
      </ScrollView>

      <Modal
        visible={editor !== null}
        animationType="slide"
        onRequestClose={() => {
          if (!profiles.busy) setEditor(null);
        }}
      >
        <SafeAreaView style={styles.screen}>
          <View style={styles.header}>
            <Text style={styles.title}>
              {profiles.vehicles.some((item) => item.id === editor?.id)
                ? "Edit vehicle"
                : "Add vehicle"}
            </Text>
            <Action
              title="Cancel"
              disabled={profiles.busy}
              onPress={() => setEditor(null)}
            />
          </View>
          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
          >
            <Text style={styles.eyebrow}>VEHICLE NAME</Text>
            <TextInput
              style={styles.input}
              accessibilityLabel="Vehicle name"
              placeholder="e.g. Citroën C4"
              placeholderTextColor="#8098B8"
              value={editor?.name ?? ""}
              maxLength={60}
              editable={!profiles.busy}
              onChangeText={(name) =>
                setEditor((current) => current && { ...current, name })
              }
            />
            <Text style={styles.eyebrow}>BLUETOOTH DEVICE</Text>
            <Text style={styles.description}>
              Choose the paired device belonging to this vehicle. Pair it in
              Android Bluetooth settings first.
            </Text>
            {editor?.bluetoothAddress && (
              <Text style={styles.selectedDevice}>
                Selected: {editor.bluetoothName || editor.bluetoothAddress}
              </Text>
            )}
            <Text style={styles.small}>
              Changing the Bluetooth device clears this vehicle's saved NFC tag.
            </Text>
            {editorError ? (
              <Text style={styles.error} accessibilityRole="alert">
                {editorError}
              </Text>
            ) : null}
            {loadingDevices ? (
              <Text style={styles.description}>Loading paired devices…</Text>
            ) : (
              <>
                {devices.length === 0 && (
                  <Text style={styles.description}>
                    No paired devices found. Enable Bluetooth and pair your
                    vehicle, then refresh.
                  </Text>
                )}
                {devices.map((device) => {
                  const selected =
                    editor?.bluetoothAddress?.toUpperCase() ===
                    device.address.toUpperCase();
                  return (
                    <Pressable
                      key={device.address}
                      disabled={profiles.busy}
                      accessibilityRole="radio"
                      accessibilityState={{ checked: selected }}
                      style={[styles.device, selected && styles.activeCard]}
                      onPress={() =>
                        setEditor(
                          (current) =>
                            current && {
                              ...current,
                              bluetoothName: device.name || "Unknown device",
                              bluetoothAddress: device.address,
                              name: current.name || device.name || "My vehicle",
                            },
                        )
                      }
                    >
                      <View style={{ flex: 1 }}>
                        <Text style={styles.deviceName}>
                          {device.name || "Unknown device"}
                        </Text>
                        <Text style={styles.small}>{device.address}</Text>
                      </View>
                      {selected && (
                        <Ionicons
                          name="checkmark-circle"
                          size={23}
                          color="#5EDB8A"
                        />
                      )}
                    </Pressable>
                  );
                })}
              </>
            )}
            <View style={styles.actions}>
              <Action
                title="Refresh devices"
                onPress={loadDevices}
                disabled={loadingDevices || profiles.busy}
              />
              <Action
                title="Enable Bluetooth"
                disabled={profiles.busy}
                onPress={() => {
                  CarlockBluetoothModule.requestEnableBluetooth().catch((err) =>
                    setEditorError(message(err)),
                  );
                }}
              />
            </View>
            <Action
              title={profiles.busy ? "Saving…" : "Save vehicle"}
              primary
              disabled={
                profiles.busy ||
                !editor?.name.trim() ||
                !editor?.bluetoothAddress
              }
              onPress={saveVehicle}
            />
          </ScrollView>
        </SafeAreaView>
      </Modal>

      <Modal
        visible={nfcTarget !== null}
        transparent
        animationType="fade"
        onRequestClose={() => {
          if (!startingNfc) setNfcTarget(null);
        }}
      >
        <View style={styles.scrim}>
          <View style={styles.prompt}>
            <Ionicons name="radio-outline" size={42} color="#5EDB8A" />
            <Text style={styles.promptTitle}>
              Configure NFC tag for {nfcTarget?.name}
            </Text>
            <Text style={styles.description}>
              Hold the back of your phone near the NFC tag on the next screen.
              Enrollment does not confirm locking.
            </Text>
            {nfcTarget?.id !== profiles.activeVehicleId && (
              <Text style={styles.description}>
                This will make {nfcTarget?.name} the active vehicle before
                setup.
              </Text>
            )}
            <Action
              title={
                startingNfc
                  ? "Opening…"
                  : nfcTarget?.id === profiles.activeVehicleId
                    ? "Start scan"
                    : "Make active & start scan"
              }
              primary
              disabled={startingNfc || profiles.busy}
              onPress={startEnrollment}
            />
            <Action
              title="Cancel"
              disabled={startingNfc}
              onPress={() => setNfcTarget(null)}
            />
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#081B3A" },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 22,
    paddingVertical: 16,
    gap: 12,
  },
  back: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: "#10264F",
    alignItems: "center",
    justifyContent: "center",
  },
  title: { color: "#EAF4FF", fontSize: 24, fontWeight: "900" },
  content: {
    paddingHorizontal: 22,
    paddingTop: 12,
    paddingBottom: 40,
    gap: 14,
  },
  eyebrow: {
    color: "#8DA9CE",
    fontSize: 12,
    fontWeight: "800",
    letterSpacing: 1.5,
    marginTop: 12,
  },
  description: { color: "#B7C9E5", fontSize: 14, lineHeight: 22 },
  card: {
    backgroundColor: "#10264F",
    borderColor: "#214B8F",
    borderWidth: 1,
    borderRadius: 22,
    padding: 18,
    gap: 12,
    marginVertical: 4,
  },
  activeCard: { borderColor: "#5EDB8A", borderWidth: 1 },
  vehicleHeader: { flexDirection: "row", alignItems: "center", gap: 12 },
  carIcon: {
    width: 46,
    height: 46,
    borderRadius: 15,
    backgroundColor: "#183766",
    alignItems: "center",
    justifyContent: "center",
  },
  vehicleName: { color: "#EAF4FF", fontSize: 18, fontWeight: "800" },
  badge: {
    color: "#5EDB8A",
    backgroundColor: "#153D3E",
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 10,
    fontSize: 12,
    fontWeight: "800",
  },
  detailRow: { flexDirection: "row", gap: 8, alignItems: "center" },
  detail: { color: "#B7C9E5", fontSize: 14, flex: 1 },
  actions: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  action: {
    backgroundColor: "#193866",
    borderRadius: 13,
    paddingHorizontal: 14,
    paddingVertical: 14,
    alignItems: "center",
  },
  primaryAction: { backgroundColor: "#245BD4" },
  actionText: { color: "#EAF4FF", fontSize: 14, fontWeight: "700" },
  danger: { color: "#FF9273" },
  divider: { height: 1, backgroundColor: "#23436F", marginVertical: 2 },
  small: { color: "#9FB7D9", fontSize: 12, lineHeight: 18 },
  error: { color: "#FF9273", fontSize: 14, lineHeight: 22 },
  notice: {
    backgroundColor: "#153D3E",
    padding: 18,
    borderRadius: 18,
    gap: 12,
  },
  input: {
    color: "#EAF4FF",
    borderColor: "#315991",
    backgroundColor: "#10264F",
    borderWidth: 1,
    borderRadius: 14,
    padding: 16,
    fontSize: 17,
  },
  selectedDevice: { color: "#5EDB8A", fontSize: 14 },
  device: {
    padding: 16,
    borderRadius: 15,
    backgroundColor: "#10264F",
    borderWidth: 1,
    borderColor: "#214B8F",
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  deviceName: {
    color: "#EAF4FF",
    fontSize: 16,
    fontWeight: "700",
    marginBottom: 4,
  },
  scrim: {
    flex: 1,
    backgroundColor: "rgba(0,8,24,0.85)",
    justifyContent: "center",
    padding: 22,
  },
  prompt: {
    backgroundColor: "#10264F",
    padding: 24,
    borderRadius: 24,
    gap: 18,
    borderColor: "#315991",
    borderWidth: 1,
  },
  promptTitle: {
    color: "#EAF4FF",
    fontSize: 23,
    lineHeight: 30,
    fontWeight: "800",
  },
});
