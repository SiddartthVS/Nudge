import React, { useEffect, useState } from 'react';
import { View } from 'react-native';
import HomeScreen from './screens/HomeScreen';
import PermissionsScreen from './screens/PermissionsScreen';
import { checkAllPermissions, isFullyGranted } from './screens/scripts/permissions';
import { Colors } from './screens/scripts/colors';

type Route = 'checking' | 'permissions' | 'home';

export default function App() {
  const [route, setRoute] = useState<Route>('checking');

  useEffect(() => {
    checkAllPermissions().then(status => {
      setRoute(isFullyGranted(status) ? 'home' : 'permissions');
    });
  }, []);

  if (route === 'checking') {
    return <View style={{ flex: 1, backgroundColor: Colors.background }} />;
  }

  if (route === 'home') {
    return <HomeScreen />;
  }

  return <PermissionsScreen onComplete={() => setRoute('home')} />;
}
