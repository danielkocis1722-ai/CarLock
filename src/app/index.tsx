import { useEffect, useState } from "react";
import {
  PermissionsAndroid,
  Platform,
  StyleSheet,
  Text,
  View,
} from "react-native";

import RNBluetoothClassic from "react-native-bluetooth-classic";
import CarlockBluetoothModule from "../../modules/carlock-bluetooth/src/CarlockBluetoothModule";

export default function HomeScreen() {
  const [carDevice, setCarDevice] = useState<any>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setupCar();

    const checkInitialState = async () => {
      const connected = await CarlockBluetoothModule.isCarConnected();

      setIsConnected(connected);
    };

    checkInitialState();

    const subscription = CarlockBluetoothModule.addListener(
      "onCarConnectionChanged",
      (event) => {
        console.log("Car Bluetooth event:", event);
        setIsConnected(event.connected);
      },
    );

    return () => {
      subscription.remove();
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

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>CarLock</Text>

      <View style={styles.card}>
        <Text style={styles.carName}>
          {carDevice ? carDevice.name : "Searching for car..."}
        </Text>

        <View
          style={[
            styles.statusDot,
            {
              backgroundColor: isConnected ? "#5EDB8A" : "#FF5C1D",
            },
          ]}
        />

        <Text
          style={[
            styles.connectionStatus,
            {
              color: isConnected ? "#5EDB8A" : "#FF5C1D",
            },
          ]}
        >
          {isConnected ? "CONNECTED" : "DISCONNECTED"}
        </Text>

        <Text style={styles.description}>
          {isConnected
            ? "CarLock detects your car."
            : "Your car is not currently connected."}
        </Text>

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
    fontSize: 34,
    fontWeight: "800",
    color: "#EAF4FF",
    marginBottom: 30,
    letterSpacing: 1,
  },

  card: {
    width: "100%",
    maxWidth: 360,
    backgroundColor: "#10264F",
    borderRadius: 24,
    padding: 28,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#214B8F",
  },

  carName: {
    color: "#EAF4FF",
    fontSize: 24,
    fontWeight: "800",
    marginBottom: 24,
  },

  statusDot: {
    width: 18,
    height: 18,
    borderRadius: 9,
    marginBottom: 16,
  },

  connectionStatus: {
    fontSize: 28,
    fontWeight: "800",
    marginBottom: 12,
  },

  description: {
    color: "#9FB7D9",
    fontSize: 15,
    textAlign: "center",
  },

  error: {
    color: "#FF5C1D",
    marginTop: 18,
    textAlign: "center",
  },
});
