import { NativeModule, requireNativeModule } from "expo";

type CarlockBluetoothEvents = {
  onFleetChanged: (event: { stateJson: string }) => void;
  onBluetoothStateChanged: (event: { enabled: boolean }) => void;
  onNfcEnrollmentFinished: (event: { success: boolean }) => void;
};
declare class CarlockBluetoothModule extends NativeModule<CarlockBluetoothEvents> {
  getFleetState(): Promise<string>;
  importLegacyProfiles(raw: string | null): Promise<string>;
  saveVehicle(
    id: string,
    name: string,
    address: string,
    bluetoothName: string | null,
  ): Promise<string>;
  deleteVehicle(id: string): Promise<string>;
  confirmVehicle(id: string, disconnectAt: number): Promise<string>;
  removeNfcTag(tagId: string): Promise<string>;
  isBluetoothEnabled(): Promise<boolean>;
  requestEnableBluetooth(): Promise<boolean>;
  configureNfcTag(): Promise<boolean>;
}
export default requireNativeModule<CarlockBluetoothModule>("CarlockBluetooth");
