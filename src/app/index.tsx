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

export default function HomeScreen() {
  const [carDevice, setCarDevice] = useState<any>(null);

  const [isConnected, setIsConnected] = useState(false);
  const [isLocked, setIsLocked] = useState(true);

  const [error, setError] = useState("");

  useEffect(() => {
    setupCar();

    const loadStoredState = async () => {
      try {
        const stored = await CarlockBluetoothModule.getStoredState();

        setIsConnected(stored.connected);
        setIsLocked(stored.locked);

        await refreshConnectionState();
      } catch (err) {
        console.log("Stored state error:", err);
      }
    };

    loadStoredState();

    const bluetoothSubscription = CarlockBluetoothModule.addListener(
      "onCarConnectionChanged",
      async (event) => {
        console.log("Car Bluetooth event:", event);

        setIsConnected(event.connected);

        if (event.connected) {
          setIsLocked(false);
        }
      },
    );

    const bluetoothStateSubscription = CarlockBluetoothModule.addListener(
      "onBluetoothStateChanged",
      async (event) => {
        console.log("Bluetooth enabled:", event.enabled);

        if (event.enabled) {
          const stored = await CarlockBluetoothModule.getStoredState();

          setIsConnected(stored.connected);
          setIsLocked(stored.locked);
        } else {
          setIsConnected(false);

          // locked stav nemeníme
        }
      },
    );

    const appStateSubscription = AppState.addEventListener(
      "change",
      async (nextState) => {
        if (nextState === "active") {
          const stored = await CarlockBluetoothModule.getStoredState();

          setIsConnected(stored.connected);
          setIsLocked(stored.locked);

          await refreshConnectionState();
        }
      },
    );

    return () => {
      bluetoothSubscription.remove();
      bluetoothStateSubscription.remove();
      appStateSubscription.remove();
    };
  }, []);

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

  const refreshConnectionState = async () => {
    try {
      const stored = await CarlockBluetoothModule.getStoredState();

      console.log("Current stored Bluetooth state:", stored);

      setIsConnected(stored.connected);
      setIsLocked(stored.locked);
    } catch (err: any) {
      console.log("Bluetooth refresh error:", err);
    }
  };

  const confirmLocked = async () => {
    setIsLocked(true);

    await CarlockBluetoothModule.setLocked(true);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>CarLock</Text>

      <View style={styles.card}>
        <Text style={styles.carName}>
          {carDevice ? carDevice.name : "Searching for car..."}
        </Text>

        <Text style={styles.smallLabel}>Your car is</Text>

        <Text
          style={[
            styles.lockStatus,
            {
              color: isLocked ? "#5EDB8A" : "#FF5C1D",
            },
          ]}
        >
          {isLocked ? "LOCKED" : "UNLOCKED"}
        </Text>

        <View style={styles.connectionRow}>
          <View
            style={[
              styles.statusDot,
              {
                backgroundColor: isConnected ? "#5EDB8A" : "#7084A8",
              },
            ]}
          />

          <Text style={styles.connectionText}>
            CITROEN {isConnected ? "connected" : "disconnected"}
          </Text>
        </View>

        {!isLocked && !isConnected && (
          <View style={styles.warning}>
            <Text style={styles.warningText}>
              Your car disconnected, but locking has not been confirmed.
            </Text>
          </View>
        )}

        {!isLocked && (
          <Pressable
            style={({ pressed }) => [
              styles.lockButton,
              pressed && {
                opacity: 0.8,
              },
            ]}
            onPress={confirmLocked}
          >
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
    justifyContent: "center",
    alignItems: "center",
    padding: 24,
  },

  logo: {
    fontSize: 38,
    fontWeight: "800",
    color: "#EAF4FF",
    marginBottom: 32,
    letterSpacing: 1,
  },

  card: {
    width: "100%",
    maxWidth: 370,
    backgroundColor: "#10264F",
    borderRadius: 26,
    padding: 28,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#214B8F",
  },

  carName: {
    color: "#EAF4FF",
    fontSize: 23,
    fontWeight: "800",
    marginBottom: 34,
  },

  smallLabel: {
    color: "#9FB7D9",
    fontSize: 17,
    marginBottom: 10,
  },

  lockStatus: {
    fontSize: 42,
    fontWeight: "900",
    marginBottom: 30,
  },

  connectionRow: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 24,
  },

  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: 9,
  },

  connectionText: {
    color: "#9FB7D9",
    fontSize: 15,
  },

  warning: {
    width: "100%",
    backgroundColor: "rgba(255, 92, 29, 0.10)",
    borderWidth: 1,
    borderColor: "#FF5C1D",
    borderRadius: 14,
    padding: 14,
    marginBottom: 18,
  },

  warningText: {
    color: "#FFB092",
    textAlign: "center",
    lineHeight: 20,
  },

  lockButton: {
    width: "100%",
    backgroundColor: "#2F66C2",
    paddingVertical: 16,
    borderRadius: 16,
    alignItems: "center",
  },

  lockButtonText: {
    color: "#EAF4FF",
    fontSize: 17,
    fontWeight: "700",
  },

  error: {
    color: "#FF5C1D",
    marginTop: 18,
    textAlign: "center",
  },
});
