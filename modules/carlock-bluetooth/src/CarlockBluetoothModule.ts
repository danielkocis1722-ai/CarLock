import { NativeModule, requireNativeModule } from "expo";

export type CarConnectionChangedEvent = {
  connected: boolean;
  name: string | null;
  address: string | null;
};

export type CarlockState = {
  connected: boolean;
  locked: boolean;
};

type CarlockBluetoothEvents = {
  onCarConnectionChanged: (event: CarConnectionChangedEvent) => void;
};

declare class CarlockBluetoothModule extends NativeModule<CarlockBluetoothEvents> {
  isCarConnected(): Promise<boolean>;

  getStoredState(): Promise<CarlockState>;

  setLocked(locked: boolean): Promise<void>;
}

export default requireNativeModule<CarlockBluetoothModule>("CarlockBluetooth");
