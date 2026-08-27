import { NativeModules, Button, View } from 'react-native';

// Make sure this name EXACTLY matches the string in your Java getName() method
const { PermissionsModule } = NativeModules; 

export default function App() {
  
  const handleOverlayRequest = () => {
    console.log("1. Button clicked!");
    console.log("2. Is Module Linked?:", PermissionsModule);
    
    if (!PermissionsModule) {
      console.error("❌ MODULE IS UNDEFINED - The Java package is not linked correctly.");
      return;
    }

    try {
      console.log("3. Calling Java method...");
      PermissionsModule.requestOverlayPermission();
    } catch (error) {
      console.error("❌ JAVA ERROR:", error);
    }
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
      <Button 
        title="Test Overlay Permission" 
        onPress={handleOverlayRequest} 
      />
    </View>
  );
}