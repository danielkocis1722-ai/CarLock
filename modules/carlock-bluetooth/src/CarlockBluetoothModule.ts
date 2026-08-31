import { NativeModule, requireNativeModule } from "expo";

export type CarConnectionChangedEvent = {
  connected: boolean;
  name: string | null;
  address: string | null;
};

type CarlockBluetoothEvents = {
  onCarConnectionChanged: (event: CarConnectionChangedEvent) => void;
};

declare class CarlockBluetoothModule extends NativeModule<CarlockBluetoothEvents> {
  isCarConnected(): Promise<boolean>;
}

export default requireNativeModule<CarlockBluetoothModule>("CarlockBluetooth");
