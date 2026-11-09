import { NativeModule, requireNativeModule } from "expo";

export type CarConnectionChangedEvent = {
  connected: boolean;
  name: string | null;
  address: string | null;
};

export type CarlockState = {
  connected: boolean;
  locked: boolean;
  lastEvent: string | null;
  lastEventAt: number;
};

export type SelectedCar = {
  name: string | null;
  address: string | null;
};

type CarlockBluetoothEvents = {
  onCarConnectionChanged: (event: CarConnectionChangedEvent) => void;

  onBluetoothStateChanged: (event: { enabled: boolean }) => void;

  onLockStateChanged: (event: { locked: boolean }) => void;

  onNfcEnrollmentFinished: (event: {
    success: boolean;
    address: string | null;
  }) => void;
};

declare class CarlockBluetoothModule extends NativeModule<CarlockBluetoothEvents> {
  isCarConnected(): Promise<boolean>;

  isBluetoothEnabled(): Promise<boolean>;

  requestEnableBluetooth(): Promise<boolean>;

  getStoredState(): Promise<CarlockState>;

  setLocked(locked: boolean): Promise<void>;

  getNfcTagId(): Promise<string | null>;

  configureNfcTag(): Promise<boolean>;

  getSelectedCar(): Promise<SelectedCar>;

  setSelectedCar(name: string, address: string): Promise<void>;

  activateVehicle(
    name: string,
    address: string,
    nfcTagId: string | null,
  ): Promise<void>;
}

export default requireNativeModule<CarlockBluetoothModule>("CarlockBluetooth");
