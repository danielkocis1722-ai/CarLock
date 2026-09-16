import Ionicons from "@expo/vector-icons/Ionicons";
import { useFocusEffect, useRouter } from "expo-router";
import { useCallback, useEffect, useState } from "react";

import {
  AppState,
  Linking,
  PermissionsAndroid,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";

import RNBluetoothClassic from "react-native-bluetooth-classic";

import CarlockBluetoothModule from "../../modules/carlock-bluetooth/src/CarlockBluetoothModule";

type CarState = "LOCKED" | "IN_CAR" | "UNCONFIRMED";

export default function HomeScreen() {
  const router = useRouter();

  const [carDevice, setCarDevice] = useState<any>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [isLocked, setIsLocked] = useState(true);
  const [bluetoothEnabled, setBluetoothEnabled] = useState(true);
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [exactAlarmEnabled, setExactAlarmEnabled] = useState(true);
  const [error, setError] = useState("");

  const carState: CarState = isLocked
    ? "LOCKED"
    : isConnected
      ? "IN_CAR"
      : "UNCONFIRMED";

  const requestBluetoothPermissions = async () => {
    if (Platform.OS !== "android") return true;

    if (Platform.Version >= 31) {
      const result = await PermissionsAndroid.requestMultiple([
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
      ]);

      return (
        result["android.permission.BLUETOOTH_CONNECT"] ===
          PermissionsAndroid.RESULTS.GRANTED &&
        result["android.permission.BLUETOOTH_SCAN"] ===
          PermissionsAndroid.RESULTS.GRANTED
      );
    }

    return true;
  };

  const setupCar = async () => {
    try {
      const granted = await requestBluetoothPermissions();

      if (!granted) {
        setError("Bluetooth permission denied.");
        return;
      }

      const devices = await RNBluetoothClassic.getBondedDevices();
      let selected = await CarlockBluetoothModule.getSelectedCar();

      if (!selected.address) {
        const citroen = devices.find(
          (device: any) => device.name?.trim().toUpperCase() === "CITROEN",
        );

        if (citroen) {
          await CarlockBluetoothModule.setSelectedCar(
            citroen.name ?? "CITROEN",
            citroen.address,
          );

          selected = {
            name: citroen.name ?? "CITROEN",
            address: citroen.address,
          };
        }
      }

      const car = devices.find(
        (device: any) =>
          device.address?.toUpperCase() === selected.address?.toUpperCase(),
      );

      if (!car) {
        setCarDevice(null);
        setError("Selected car was not found in paired devices.");
        return;
      }

      setCarDevice(car);
      setError("");
    } catch (err: any) {
      setError(err?.message || String(err));
    }
  };

  const checkNotificationPermission = async () => {
    if (Platform.OS !== "android" || Platform.Version < 33) {
      setNotificationsEnabled(true);
      return;
    }

    const granted = await PermissionsAndroid.check(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    );

    setNotificationsEnabled(granted);
  };

  const requestNotificationPermission = async () => {
    if (Platform.OS !== "android" || Platform.Version < 33) {
      setNotificationsEnabled(true);
      return;
    }

    try {
      const result = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
      );

      setNotificationsEnabled(
        result === PermissionsAndroid.RESULTS.GRANTED,
      );

      if (result === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN) {
        await Linking.openSettings();
      }
    } catch (err) {
      console.log("Notification permission error:", err);
    }
  };

  const checkExactAlarmPermission = async () => {
    try {
      const allowed = await CarlockBluetoothModule.canScheduleExactAlarms();
      setExactAlarmEnabled(allowed);
    } catch (err) {
      console.log("Exact alarm permission check error:", err);
      setExactAlarmEnabled(false);
    }
  };

  const requestExactAlarmPermission = async () => {
    try {
      const opened =
        await CarlockBluetoothModule.requestExactAlarmPermission();

      if (!opened) {
        setError("Could not open precise reminder settings.");
      }
    } catch (err: any) {
      setError(err?.message || String(err));
    }
  };

  const turnOnBluetooth = async () => {
    try {
      const opened = await CarlockBluetoothModule.requestEnableBluetooth();

      if (!opened) {
        setError("Could not open Bluetooth settings.");
      }
    } catch (err: any) {
      setError(err?.message || String(err));
    }
  };

  const confirmLocked = async () => {
    setIsLocked(true);
    await CarlockBluetoothModule.setLocked(true);
  };

  const refreshState = async () => {
    try {
      const stored = await CarlockBluetoothModule.getStoredState();
      const bluetoothOn = await CarlockBluetoothModule.isBluetoothEnabled();

      setIsConnected(stored.connected);
      setIsLocked(stored.locked);
      setBluetoothEnabled(bluetoothOn);
    } catch (err) {
      console.log("State refresh error:", err);
    }
  };

  useFocusEffect(
    useCallback(() => {
      setupCar();
      refreshState();
      checkNotificationPermission();
      checkExactAlarmPermission();
    }, []),
  );

  useEffect(() => {
    const carSubscription = CarlockBluetoothModule.addListener(
      "onCarConnectionChanged",
      (event) => {
        console.log("Car event:", event);
        setIsConnected(event.connected);

        if (event.connected) {
          setIsLocked(false);
        }
      },
    );

    const bluetoothSubscription = CarlockBluetoothModule.addListener(
      "onBluetoothStateChanged",
      async (event) => {
        console.log("Bluetooth event:", event);
        setBluetoothEnabled(event.enabled);

        if (!event.enabled) {
          setIsConnected(false);
          return;
        }

        const stored = await CarlockBluetoothModule.getStoredState();
        setIsConnected(stored.connected);
        setIsLocked(stored.locked);
      },
    );

    const lockStateSubscription = CarlockBluetoothModule.addListener(
      "onLockStateChanged",
      (event) => {
        console.log("Lock state event:", event);
        setIsLocked(event.locked);
      },
    );

    const appStateSubscription = AppState.addEventListener(
      "change",
      async (nextState) => {
        if (nextState !== "active") return;

        await checkNotificationPermission();
        await checkExactAlarmPermission();
        await setupCar();
        await refreshState();
      },
    );

    return () => {
      carSubscription.remove();
      bluetoothSubscription.remove();
      lockStateSubscription.remove();
      appStateSubscription.remove();
    };
  }, []);

  const carName = carDevice?.name ?? "No car selected";

  const stateConfig = {
    LOCKED: {
      title: "LOCKED",
      subtitle: "Your car is secured.",
      icon: "lock-closed" as const,
      color: "#5EDB8A",
    },
    IN_CAR: {
      title: "IN CAR",
      subtitle: `${carName} is connected.`,
      icon: "car-sport" as const,
      color: "#EAF4FF",
    },
    UNCONFIRMED: {
      title: "UNLOCKED",
      subtitle: "Locking has not been confirmed.",
      icon: "lock-open" as const,
      color: "#FF5C1D",
    },
  };

  const current = stateConfig[carState];

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerTitle}>
          <Text style={styles.logo}>CarLock</Text>
          <Text style={styles.carName} numberOfLines={1}>
            {carName}
          </Text>
        </View>

        <View style={styles.headerActions}>
          <View
            style={[
              styles.bluetoothPill,
              !bluetoothEnabled && styles.bluetoothPillOff,
            ]}
          >
            <View
              style={[
                styles.dot,
                {
                  backgroundColor: bluetoothEnabled ? "#5EDB8A" : "#FF5C1D",
                },
              ]}
            />
            <Text style={styles.bluetoothPillText}>
              Bluetooth {bluetoothEnabled ? "On" : "Off"}
            </Text>
          </View>

          <Pressable
            style={({ pressed }) => [
              styles.settingsButton,
              pressed && { opacity: 0.7 },
            ]}
            onPress={() => router.push("/settings")}
          >
            <Ionicons name="settings-outline" size={22} color="#EAF4FF" />
          </Pressable>
        </View>
      </View>

      <View style={styles.mainCard}>
        <View style={[styles.iconCircle, { borderColor: current.color }]}>
          <View
            style={[styles.iconGlow, { backgroundColor: current.color }]}
          />
          <Ionicons name={current.icon} size={68} color={current.color} />
        </View>

        <Text style={[styles.stateTitle, { color: current.color }]}>
          {current.title}
        </Text>
        <Text style={styles.stateSubtitle}>{current.subtitle}</Text>

        <View style={styles.connectionCard}>
          <View style={styles.connectionLeft}>
            <View
              style={[
                styles.connectionIcon,
                {
                  backgroundColor: isConnected
                    ? "rgba(94,219,138,0.12)"
                    : "rgba(159,183,217,0.08)",
                },
              ]}
            >
              <Ionicons
                name="car-outline"
                size={22}
                color={isConnected ? "#5EDB8A" : "#9FB7D9"}
              />
            </View>

            <View style={{ flex: 1 }}>
              <Text style={styles.connectionTitle} numberOfLines={1}>
                {carName}
              </Text>
              <Text style={styles.connectionSubtitle}>Car connection</Text>
            </View>
          </View>

          <Text
            style={[
              styles.connectionStatus,
              { color: isConnected ? "#5EDB8A" : "#9FB7D9" },
            ]}
          >
            {isConnected ? "Connected" : "Disconnected"}
          </Text>
        </View>

        {!bluetoothEnabled && (
          <View style={styles.warningCard}>
            <View style={styles.warningTop}>
              <View style={styles.warningIcon}>
                <Ionicons name="bluetooth" size={25} color="#FF5C1D" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.warningCardTitle}>Bluetooth is off</Text>
                <Text style={styles.warningCardText}>
                  Turn it on so CarLock can detect your car.
                </Text>
              </View>
            </View>

            <Pressable
              style={({ pressed }) => [
                styles.warningButton,
                pressed && { opacity: 0.8 },
              ]}
              onPress={turnOnBluetooth}
            >
              <Ionicons name="bluetooth" size={20} color="#FFFFFF" />
              <Text style={styles.warningButtonText}>Turn on Bluetooth</Text>
            </Pressable>
          </View>
        )}

        {!notificationsEnabled && (
          <View style={styles.warningCard}>
            <View style={styles.warningTop}>
              <View style={styles.warningIcon}>
                <Ionicons
                  name="notifications-off-outline"
                  size={25}
                  color="#FF5C1D"
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.warningCardTitle}>
                  Notifications are off
                </Text>
                <Text style={styles.warningCardText}>
                  Enable notifications so CarLock can remind you when locking
                  has not been confirmed.
                </Text>
              </View>
            </View>

            <Pressable
              style={({ pressed }) => [
                styles.warningButton,
                pressed && { opacity: 0.8 },
              ]}
              onPress={requestNotificationPermission}
            >
              <Ionicons
                name="notifications-outline"
                size={20}
                color="#FFFFFF"
              />
              <Text style={styles.warningButtonText}>Enable notifications</Text>
            </Pressable>
          </View>
        )}

        {!exactAlarmEnabled && (
          <View style={styles.warningCard}>
            <View style={styles.warningTop}>
              <View style={styles.warningIcon}>
                <Ionicons name="alarm-outline" size={25} color="#FF5C1D" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.warningCardTitle}>
                  Precise reminders are off
                </Text>
                <Text style={styles.warningCardText}>
                  Allow Alarms & reminders so the lock reminder can fire on
                  time while the phone is idle.
                </Text>
              </View>
            </View>

            <Pressable
              style={({ pressed }) => [
                styles.warningButton,
                pressed && { opacity: 0.8 },
              ]}
              onPress={requestExactAlarmPermission}
            >
              <Ionicons name="alarm-outline" size={20} color="#FFFFFF" />
              <Text style={styles.warningButtonText}>
                Enable precise reminders
              </Text>
            </Pressable>
          </View>
        )}

        {carState === "UNCONFIRMED" && (
          <View style={styles.unconfirmedWarning}>
            <Ionicons name="alert-circle-outline" size={24} color="#FF5C1D" />
            <View style={{ flex: 1 }}>
              <Text style={styles.unconfirmedTitle}>Locking not confirmed</Text>
              <Text style={styles.unconfirmedText}>
                You left the car, but CarLock has not received lock confirmation
                yet.
              </Text>
            </View>
          </View>
        )}

        {carState === "UNCONFIRMED" && (
          <Pressable
            style={({ pressed }) => [
              styles.lockButton,
              pressed && { opacity: 0.82 },
            ]}
            onPress={confirmLocked}
          >
            <Ionicons name="lock-closed" size={20} color="#FFFFFF" />
            <Text style={styles.lockButtonText}>I locked the car</Text>
          </Pressable>
        )}

        {error ? <Text style={styles.error}>{error}</Text> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#081B3A",
    paddingHorizontal: 22,
    paddingTop: 68,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 28,
  },
  headerTitle: {
    flex: 1,
    marginRight: 12,
  },
  headerActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  logo: {
    color: "#EAF4FF",
    fontSize: 32,
    fontWeight: "900",
    letterSpacing: 0.5,
  },
  carName: {
    color: "#7084A8",
    fontSize: 13,
    fontWeight: "700",
    marginTop: 3,
    letterSpacing: 1.2,
  },
  bluetoothPill: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 11,
    paddingVertical: 8,
    backgroundColor: "rgba(94,219,138,0.08)",
    borderRadius: 999,
  },
  bluetoothPillOff: {
    backgroundColor: "rgba(255,92,29,0.09)",
  },
  bluetoothPillText: {
    color: "#B7C9E5",
    fontSize: 11,
    fontWeight: "700",
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    marginRight: 7,
  },
  settingsButton: {
    width: 40,
    height: 40,
    borderRadius: 13,
    backgroundColor: "#10264F",
    borderWidth: 1,
    borderColor: "#214B8F",
    justifyContent: "center",
    alignItems: "center",
  },
  mainCard: {
    width: "100%",
    backgroundColor: "#10264F",
    borderRadius: 30,
    padding: 24,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#214B8F",
  },
  iconCircle: {
    width: 160,
    height: 160,
    borderRadius: 80,
    borderWidth: 2,
    justifyContent: "center",
    alignItems: "center",
    marginTop: 10,
    marginBottom: 25,
    overflow: "hidden",
  },
  iconGlow: {
    position: "absolute",
    width: 95,
    height: 95,
    borderRadius: 48,
    opacity: 0.08,
  },
  stateTitle: {
    fontSize: 41,
    fontWeight: "900",
    letterSpacing: 1,
  },
  stateSubtitle: {
    color: "#9FB7D9",
    fontSize: 15,
    marginTop: 6,
    marginBottom: 28,
    textAlign: "center",
  },
  connectionCard: {
    width: "100%",
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 15,
    backgroundColor: "rgba(255,255,255,0.035)",
    borderRadius: 17,
    marginBottom: 15,
  },
  connectionLeft: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    marginRight: 10,
  },
  connectionIcon: {
    width: 44,
    height: 44,
    borderRadius: 13,
    justifyContent: "center",
    alignItems: "center",
    marginRight: 12,
  },
  connectionTitle: {
    color: "#EAF4FF",
    fontSize: 15,
    fontWeight: "800",
  },
  connectionSubtitle: {
    color: "#7084A8",
    fontSize: 12,
    marginTop: 2,
  },
  connectionStatus: {
    fontSize: 12,
    fontWeight: "800",
  },
  warningCard: {
    width: "100%",
    backgroundColor: "rgba(255,92,29,0.08)",
    borderWidth: 1,
    borderColor: "rgba(255,92,29,0.35)",
    borderRadius: 18,
    padding: 16,
    marginBottom: 15,
  },
  warningTop: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 15,
  },
  warningIcon: {
    width: 45,
    height: 45,
    borderRadius: 14,
    backgroundColor: "rgba(255,92,29,0.12)",
    justifyContent: "center",
    alignItems: "center",
    marginRight: 13,
  },
  warningCardTitle: {
    color: "#EAF4FF",
    fontSize: 15,
    fontWeight: "800",
  },
  warningCardText: {
    color: "#9FB7D9",
    fontSize: 13,
    lineHeight: 18,
    marginTop: 3,
  },
  warningButton: {
    width: "100%",
    backgroundColor: "#FF5C1D",
    borderRadius: 14,
    paddingVertical: 14,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 8,
  },
  warningButtonText: {
    color: "#FFFFFF",
    fontWeight: "800",
    fontSize: 15,
  },
  unconfirmedWarning: {
    width: "100%",
    flexDirection: "row",
    gap: 12,
    backgroundColor: "rgba(255,92,29,0.08)",
    borderWidth: 1,
    borderColor: "rgba(255,92,29,0.32)",
    borderRadius: 17,
    padding: 15,
    marginBottom: 15,
  },
  unconfirmedTitle: {
    color: "#FF8A5B",
    fontSize: 14,
    fontWeight: "800",
    marginBottom: 3,
  },
  unconfirmedText: {
    color: "#9FB7D9",
    fontSize: 13,
    lineHeight: 18,
  },
  lockButton: {
    width: "100%",
    backgroundColor: "#2F66C2",
    borderRadius: 16,
    paddingVertical: 16,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 9,
  },
  lockButtonText: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "800",
  },
  error: {
    color: "#FF5C1D",
    textAlign: "center",
    marginTop: 15,
    fontSize: 13,
  },
});
