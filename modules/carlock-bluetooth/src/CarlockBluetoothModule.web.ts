import { registerWebModule, NativeModule } from 'expo';

class CarlockBluetoothModule extends NativeModule<{}> {}

export default registerWebModule(CarlockBluetoothModule, 'CarlockBluetoothModule');
