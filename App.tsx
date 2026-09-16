import { useState, useEffect } from 'react';
import { NativeModules, Button, View, AppState } from 'react-native';
import HomeScreen from './screens/HomeScreen';
import PermissionScreen from './screens/PermissionsScreen';
const { PermissionsModule } = NativeModules; 

export default function App() {
  const [overlayGranted, setOverlayGranted] = useState(false);
  const [accessibilityGranted, setAccessibilityGranted] = useState(false);
  
  const verifyPermissions = async () => {
    try {
      const overlayPermission = await PermissionsModule.checkOverlayPermission();
      const accessibilityPermission = await PermissionsModule.checkAccessibilityPermission();
      
      setOverlayGranted(overlayPermission);
      setAccessibilityGranted(accessibilityPermission);
    } catch (error) {
      console.error("Error checking permissions:", error);
    }
  };

  useEffect(() => {
    verifyPermissions();

    const subscription =  AppState.addEventListener('change', nextAppState => {
      if (nextAppState === 'active') {
        verifyPermissions();
      }
    });

    return () => {
      subscription.remove();
    };
  }, [])


  
  if(overlayGranted && accessibilityGranted) {
    return (
      <PermissionScreen />
    );
  }
  else {
    return (
      <PermissionScreen />
    )
  }
}