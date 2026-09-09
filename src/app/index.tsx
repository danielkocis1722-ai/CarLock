import Ionicons from "@expo/vector-icons/Ionicons";
import { useEffect, useState } from "react";

import {
  AppState,
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
  const [carDevice, setCarDevice] = useState<any>(null);

  const [isConnected, setIsConnected] = useState(false);

  const [isLocked, setIsLocked] = useState(true);

  const [bluetoothEnabled, setBluetoothEnabled] = useState(true);

  const [error, setError] = useState("");

  //
  // TRI HLAVNÉ STAVY CARLOCKU
  //
  const carState: CarState = isLocked
    ? "LOCKED"
    : isConnected
      ? "IN_CAR"
      : "UNCONFIRMED";

  useEffect(() => {
    setupCar();

    const loadInitialState = async () => {
      try {
        const stored = await CarlockBluetoothModule.getStoredState();

        const bluetoothOn = await CarlockBluetoothModule.isBluetoothEnabled();

        setIsConnected(stored.connected);

        setIsLocked(stored.locked);

        setBluetoothEnabled(bluetoothOn);
      } catch (err) {
        console.log("Initial state error:", err);
      }
    };

    loadInitialState();

    //
    // CITROEN CONNECT / DISCONNECT
    //
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

    //
    // BLUETOOTH TELEFÓNU ON / OFF
    //
    const bluetoothSubscription = CarlockBluetoothModule.addListener(
      "onBluetoothStateChanged",
      async (event) => {
        console.log("Bluetooth event:", event);

        setBluetoothEnabled(event.enabled);

        if (!event.enabled) {
          //
          // Bluetooth OFF =
          // auto nemôže byť connected.
          //
          setIsConnected(false);

          //
          // isLocked nemeníme.
          //
          return;
        }

        //
        // Bluetooth bol práve zapnutý.
        // Načítame uložený stav,
        // ale samotné ON nikdy
        // neznamená UNLOCKED.
        //
        const stored = await CarlockBluetoothModule.getStoredState();

        setIsConnected(stored.connected);

        setIsLocked(stored.locked);
      },
    );

    //
    // APP SA VRÁTI DO FOREGROUNDU
    //
    const appStateSubscription = AppState.addEventListener(
      "change",
      async (nextState) => {
        if (nextState !== "active") {
          return;
        }

        const stored = await CarlockBluetoothModule.getStoredState();

        const bluetoothOn = await CarlockBluetoothModule.isBluetoothEnabled();

        setIsConnected(stored.connected);

        setIsLocked(stored.locked);

        setBluetoothEnabled(bluetoothOn);
      },
    );

    return () => {
      carSubscription.remove();
      bluetoothSubscription.remove();
      appStateSubscription.remove();
    };
  }, []);

  const requestBluetoothPermissions = async () => {
    if (Platform.OS !== "android") {
      return true;
    }

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

      const citroen = devices.find(
        (device: any) => device.name?.trim().toUpperCase() === "CITROEN",
      );

      if (!citroen) {
        setError("CITROEN was not found in paired devices.");

        return;
      }

      setCarDevice(citroen);
    } catch (err: any) {
      setError(err?.message || String(err));
    }
  };

  //
  // Android systémový Bluetooth dialog
  //
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

  //
  // Zatiaľ manuálne.
  // Neskôr to spraví NFC.
  //
  const confirmLocked = async () => {
    setIsLocked(true);

    await CarlockBluetoothModule.setLocked(true);
  };

  const stateConfig = {
    LOCKED: {
      title: "LOCKED",
      subtitle: "Your car is secured.",
      icon: "lock-closed" as const,
      color: "#5EDB8A",
    },

    IN_CAR: {
      title: "IN CAR",
      subtitle: "CITROEN is connected.",
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
        <View>
          <Text style={styles.logo}>CarLock</Text>

          <Text style={styles.carName}>{carDevice?.name ?? "CITROEN"}</Text>
        </View>

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
      </View>

      <View style={styles.mainCard}>
        <View
          style={[
            styles.iconCircle,
            {
              borderColor: current.color,
            },
          ]}
        >
          <View
            style={[
              styles.iconGlow,
              {
                backgroundColor: current.color,
              },
            ]}
          />

          <Ionicons name={current.icon} size={68} color={current.color} />
        </View>

        <Text
          style={[
            styles.stateTitle,
            {
              color: current.color,
            },
          ]}
        >
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

            <View>
              <Text style={styles.connectionTitle}>CITROEN</Text>

              <Text style={styles.connectionSubtitle}>Car connection</Text>
            </View>
          </View>

          <Text
            style={[
              styles.connectionStatus,
              {
                color: isConnected ? "#5EDB8A" : "#9FB7D9",
              },
            ]}
          >
            {isConnected ? "Connected" : "Disconnected"}
          </Text>
        </View>

        {!bluetoothEnabled && (
          <View style={styles.bluetoothCard}>
            <View style={styles.bluetoothTop}>
              <View style={styles.bluetoothIcon}>
                <Ionicons name="bluetooth" size={25} color="#FF5C1D" />
              </View>

              <View
                style={{
                  flex: 1,
                }}
              >
                <Text style={styles.bluetoothTitle}>Bluetooth is off</Text>

                <Text style={styles.bluetoothText}>
                  Turn it on so CarLock can detect your car.
                </Text>
              </View>
            </View>

            <Pressable
              style={({ pressed }) => [
                styles.bluetoothButton,
                pressed && {
                  opacity: 0.8,
                },
              ]}
              onPress={turnOnBluetooth}
            >
              <Ionicons name="bluetooth" size={20} color="#FFFFFF" />

              <Text style={styles.bluetoothButtonText}>Turn on Bluetooth</Text>
            </Pressable>
          </View>
        )}

        {carState === "UNCONFIRMED" && (
          <View style={styles.warning}>
            <Ionicons name="alert-circle-outline" size={24} color="#FF5C1D" />

            <View
              style={{
                flex: 1,
              }}
            >
              <Text style={styles.warningTitle}>Locking not confirmed</Text>

              <Text style={styles.warningText}>
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
              pressed && {
                opacity: 0.82,
              },
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
    letterSpacing: 1.4,
  },

  bluetoothPill: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: "rgba(94,219,138,0.08)",
    borderRadius: 999,
  },

  bluetoothPillOff: {
    backgroundColor: "rgba(255,92,29,0.09)",
  },

  bluetoothPillText: {
    color: "#B7C9E5",
    fontSize: 12,
    fontWeight: "700",
  },

  dot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    marginRight: 7,
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
    flexDirection: "row",
    alignItems: "center",
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

  bluetoothCard: {
    width: "100%",
    backgroundColor: "rgba(255,92,29,0.08)",
    borderWidth: 1,
    borderColor: "rgba(255,92,29,0.35)",
    borderRadius: 18,
    padding: 16,
    marginBottom: 15,
  },

  bluetoothTop: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 15,
  },

  bluetoothIcon: {
    width: 45,
    height: 45,
    borderRadius: 14,
    backgroundColor: "rgba(255,92,29,0.12)",
    justifyContent: "center",
    alignItems: "center",
    marginRight: 13,
  },

  bluetoothTitle: {
    color: "#EAF4FF",
    fontSize: 15,
    fontWeight: "800",
  },

  bluetoothText: {
    color: "#9FB7D9",
    fontSize: 13,
    lineHeight: 18,
    marginTop: 3,
  },

  bluetoothButton: {
    width: "100%",
    backgroundColor: "#FF5C1D",
    borderRadius: 14,
    paddingVertical: 14,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 8,
  },

  bluetoothButtonText: {
    color: "#FFFFFF",
    fontWeight: "800",
    fontSize: 15,
  },

  warning: {
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

  warningTitle: {
    color: "#FF8A5B",
    fontSize: 14,
    fontWeight: "800",
    marginBottom: 3,
  },

  warningText: {
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
