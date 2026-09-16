import Ionicons from "@expo/vector-icons/Ionicons";
import { useRouter } from "expo-router";
import { useEffect, useState } from "react";

import {
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";

import RNBluetoothClassic from "react-native-bluetooth-classic";

import CarlockBluetoothModule, {
  SelectedCar,
} from "../../modules/carlock-bluetooth/src/CarlockBluetoothModule";

type BluetoothDevice = {
  name?: string | null;
  address: string;
};

export default function SettingsScreen() {
  const router = useRouter();

  const [selectedCar, setSelectedCar] = useState<SelectedCar>({
    name: null,
    address: null,
  });

  const [devices, setDevices] = useState<BluetoothDevice[]>([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  useEffect(() => {
    loadSettings();
  }, []);

  const requestPermissions = async () => {
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

  const loadSettings = async () => {
    try {
      setLoading(true);

      const granted = await requestPermissions();

      if (!granted) {
        setError("Bluetooth permission denied.");

        return;
      }

      const current = await CarlockBluetoothModule.getSelectedCar();

      setSelectedCar(current);

      const bonded = await RNBluetoothClassic.getBondedDevices();

      setDevices(bonded);
    } catch (err: any) {
      setError(err?.message || String(err));
    } finally {
      setLoading(false);
    }
  };

  const chooseCar = async (device: BluetoothDevice) => {
    try {
      const name = device.name || "Unknown device";

      await CarlockBluetoothModule.setSelectedCar(name, device.address);

      setSelectedCar({
        name,
        address: device.address,
      });

      router.back();
    } catch (err: any) {
      setError(err?.message || String(err));
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Pressable style={styles.backButton} onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={26} color="#EAF4FF" />
        </Pressable>

        <Text style={styles.title}>Settings</Text>

        <View
          style={{
            width: 44,
          }}
        />
      </View>

      <ScrollView showsVerticalScrollIndicator={false}>
        <Text style={styles.sectionLabel}>CURRENT CAR</Text>

        <View style={styles.currentCard}>
          <View style={styles.carIcon}>
            <Ionicons name="car-sport" size={28} color="#5EDB8A" />
          </View>

          <View style={{ flex: 1 }}>
            <Text style={styles.currentName}>
              {selectedCar.name || "CITROEN"}
            </Text>

            <Text style={styles.address}>
              {selectedCar.address || "Current default car"}
            </Text>
          </View>

          <Ionicons name="checkmark-circle" size={24} color="#5EDB8A" />
        </View>

        <Text
          style={[
            styles.sectionLabel,
            {
              marginTop: 30,
            },
          ]}
        >
          CHANGE CAR
        </Text>

        <Text style={styles.description}>
          Choose one of your paired Bluetooth devices.
        </Text>

        {loading ? (
          <Text style={styles.loading}>Loading paired devices...</Text>
        ) : (
          devices.map((device) => {
            const selected =
              selectedCar.address?.toUpperCase() ===
              device.address?.toUpperCase();

            return (
              <Pressable
                key={device.address}
                style={({ pressed }) => [
                  styles.deviceCard,

                  selected && styles.deviceCardSelected,

                  pressed && {
                    opacity: 0.75,
                  },
                ]}
                onPress={() => chooseCar(device)}
              >
                <View style={styles.deviceIcon}>
                  <Ionicons
                    name="bluetooth"
                    size={22}
                    color={selected ? "#5EDB8A" : "#9FB7D9"}
                  />
                </View>

                <View
                  style={{
                    flex: 1,
                  }}
                >
                  <Text style={styles.deviceName}>
                    {device.name || "Unknown device"}
                  </Text>

                  <Text style={styles.deviceAddress}>{device.address}</Text>
                </View>

                {selected && (
                  <Ionicons name="checkmark" size={22} color="#5EDB8A" />
                )}
              </Pressable>
            );
          })
        )}

        {error ? <Text style={styles.error}>{error}</Text> : null}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#081B3A",
    paddingTop: 58,
    paddingHorizontal: 22,
  },

  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 35,
  },

  backButton: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: "#10264F",
    justifyContent: "center",
    alignItems: "center",
  },

  title: {
    color: "#EAF4FF",
    fontSize: 23,
    fontWeight: "900",
  },

  sectionLabel: {
    color: "#7084A8",
    fontSize: 12,
    fontWeight: "800",
    letterSpacing: 1.4,
    marginBottom: 12,
  },

  currentCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#10264F",
    borderWidth: 1,
    borderColor: "#214B8F",
    borderRadius: 20,
    padding: 16,
  },

  carIcon: {
    width: 52,
    height: 52,
    borderRadius: 16,
    backgroundColor: "rgba(94,219,138,0.10)",
    justifyContent: "center",
    alignItems: "center",
    marginRight: 14,
  },

  currentName: {
    color: "#EAF4FF",
    fontSize: 17,
    fontWeight: "800",
  },

  address: {
    color: "#7084A8",
    fontSize: 11,
    marginTop: 4,
  },

  description: {
    color: "#9FB7D9",
    fontSize: 14,
    marginBottom: 15,
  },

  deviceCard: {
    width: "100%",
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "rgba(255,255,255,0.035)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.05)",
    borderRadius: 17,
    padding: 14,
    marginBottom: 10,
  },

  deviceCardSelected: {
    borderColor: "rgba(94,219,138,0.45)",
    backgroundColor: "rgba(94,219,138,0.06)",
  },

  deviceIcon: {
    width: 43,
    height: 43,
    borderRadius: 13,
    backgroundColor: "rgba(159,183,217,0.08)",
    justifyContent: "center",
    alignItems: "center",
    marginRight: 12,
  },

  deviceName: {
    color: "#EAF4FF",
    fontSize: 15,
    fontWeight: "700",
  },

  deviceAddress: {
    color: "#7084A8",
    fontSize: 11,
    marginTop: 3,
  },

  loading: {
    color: "#9FB7D9",
    marginTop: 10,
  },

  error: {
    color: "#FF5C1D",
    marginVertical: 20,
    textAlign: "center",
  },
});
