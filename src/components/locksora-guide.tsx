import Ionicons from "@expo/vector-icons/Ionicons";
import { useEffect, useRef, useState } from "react";
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

const pages = [
  {
    icon: "shield-checkmark-outline",
    title: "Welcome to Locksora",
    text: "A little peace of mind after every drive. Locksora reminds you to lock your vehicle and lets you confirm when you have.",
    hint: "Locksora records your confirmation. It does not lock the vehicle for you.",
  },
  {
    icon: "car-sport-outline",
    title: "Choose your vehicle",
    text: "In Settings, add your vehicle and choose its paired Bluetooth device. Give it a name you recognize.",
    hint: "Only the active vehicle is monitored.",
  },
  {
    icon: "radio-outline",
    title: "Configure an NFC tag",
    text: "Choose Configure NFC tag for your vehicle, then hold the back of your phone near the tag to associate it once.",
    hint: "Setting up a tag does not confirm that your vehicle is locked.",
  },
  {
    icon: "notifications-outline",
    title: "Leave the vehicle",
    text: "When your active vehicle disconnects from Bluetooth, locking becomes unconfirmed and Locksora immediately shows a reminder.",
    hint: "Allow notifications so your reminder can appear.",
  },
  {
    icon: "lock-closed-outline",
    title: "Confirm the lock",
    text: "Lock your vehicle, unlock your phone, and scan its configured NFC tag. Locksora records LOCKED and clears the reminder.",
    hint: "On Android, passive NFC tag confirmation generally requires an unlocked phone. Scanning through the lock screen is not supported.",
  },
  {
    icon: "swap-horizontal-outline",
    title: "Room for every vehicle",
    text: "Keep multiple vehicles in Settings and switch the active vehicle whenever you need. Each vehicle keeps its own Bluetooth device and NFC tag.",
    hint: "Tap the ? button on the home screen to open this guide again.",
  },
] as const;

export function LocksoraGuide({
  visible,
  onClose,
  error,
}: {
  visible: boolean;
  onClose(): void;
  error: string;
}) {
  const scroll = useRef<ScrollView>(null);
  const { width: screenWidth } = useWindowDimensions();
  const [width, setWidth] = useState(screenWidth - 44);
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (visible) setIndex(0);
  }, [visible]);

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.brand}>Locksora</Text>
          <Pressable accessibilityRole="button" onPress={onClose} hitSlop={12}>
            <Text style={styles.link}>Skip</Text>
          </Pressable>
        </View>
        <View
          style={styles.viewport}
          onLayout={(event) => setWidth(event.nativeEvent.layout.width)}
        >
          <ScrollView
            key={visible ? "open" : "closed"}
            ref={scroll}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            onMomentumScrollEnd={(event) =>
              setIndex(Math.round(event.nativeEvent.contentOffset.x / width))
            }
          >
            {pages.map((page, number) => (
              <ScrollView
                key={page.title}
                style={{ width }}
                contentContainerStyle={styles.page}
              >
                <View style={styles.icon}>
                  <Ionicons name={page.icon} size={64} color="#5EDB8A" />
                </View>
                <Text style={styles.step}>
                  YOUR GUIDE · {number + 1} OF {pages.length}
                </Text>
                <Text style={styles.title} accessibilityRole="header">
                  {page.title}
                </Text>
                <Text style={styles.body}>{page.text}</Text>
                <View style={styles.note}>
                  <Text style={styles.hint}>{page.hint}</Text>
                </View>
              </ScrollView>
            ))}
          </ScrollView>
        </View>
        <View
          style={styles.dots}
          accessibilityLabel={`Page ${index + 1} of ${pages.length}`}
        >
          {pages.map((page, number) => (
            <View
              key={page.title}
              style={[styles.dot, index === number && styles.activeDot]}
            />
          ))}
        </View>
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <Pressable
          style={styles.button}
          accessibilityRole="button"
          onPress={() => {
            if (index === pages.length - 1) onClose();
            else {
              scroll.current?.scrollTo({
                x: (index + 1) * width,
                animated: true,
              });
              setIndex(index + 1);
            }
          }}
        >
          <Text style={styles.buttonText}>
            {index === pages.length - 1 ? "Done" : "Next"}
          </Text>
        </Pressable>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#081B3A",
    paddingHorizontal: 22,
    paddingBottom: 20,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 20,
  },
  brand: { color: "#EAF4FF", fontSize: 26, fontWeight: "900" },
  link: { color: "#B7C9E5", fontSize: 16, fontWeight: "700", padding: 8 },
  viewport: { flex: 1 },
  page: {
    flexGrow: 1,
    justifyContent: "center",
    paddingVertical: 24,
    paddingHorizontal: 6,
  },
  icon: {
    alignSelf: "flex-start",
    backgroundColor: "#10264F",
    borderRadius: 28,
    padding: 26,
    marginBottom: 32,
  },
  step: {
    color: "#8DA9CE",
    fontSize: 12,
    letterSpacing: 1.5,
    fontWeight: "700",
    marginBottom: 16,
  },
  title: {
    color: "#EAF4FF",
    fontSize: 32,
    lineHeight: 38,
    fontWeight: "900",
    marginBottom: 18,
  },
  body: { color: "#B7C9E5", fontSize: 18, lineHeight: 28 },
  note: {
    backgroundColor: "#10264F",
    borderRadius: 18,
    padding: 18,
    marginTop: 26,
  },
  hint: { color: "#B7C9E5", fontSize: 14, lineHeight: 22 },
  dots: {
    flexDirection: "row",
    gap: 8,
    alignSelf: "center",
    paddingVertical: 24,
  },
  dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: "#35527B" },
  activeDot: { width: 22, backgroundColor: "#5EDB8A" },
  button: {
    backgroundColor: "#245BD4",
    padding: 18,
    borderRadius: 18,
    alignItems: "center",
  },
  buttonText: { color: "#FFFFFF", fontSize: 16, fontWeight: "800" },
  error: { color: "#FF9273", marginBottom: 12 },
});
